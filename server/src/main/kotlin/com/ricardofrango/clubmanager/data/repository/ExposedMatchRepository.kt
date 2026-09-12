package com.ricardofrango.clubmanager.data.repository

import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.exception.DatabaseException
import com.ricardofrango.clubmanager.model.CallUpStatus
import com.ricardofrango.clubmanager.model.Match
import com.ricardofrango.clubmanager.model.MatchCallUp
import com.ricardofrango.clubmanager.model.MatchCallUpWithPlayer
import com.ricardofrango.clubmanager.model.MatchDetails
import com.ricardofrango.clubmanager.model.MatchStatus
import com.ricardofrango.clubmanager.model.Opponent
import com.ricardofrango.clubmanager.model.Player
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid
import com.ricardofrango.clubmanager.data.database.MatchCallUps as CallUpsTable
import com.ricardofrango.clubmanager.data.database.Matches as MatchesTable
import com.ricardofrango.clubmanager.data.database.Opponents as OpponentsTable
import com.ricardofrango.clubmanager.data.database.Players as PlayersTable

class ExposedMatchRepository : MatchRepository {

    override suspend fun getMatchById(id: String): Match? =
        MatchesTable.selectAll().where { MatchesTable.id eq Uuid.parse(id) }
            .map(::toMatch)
            .singleOrNull()

    override suspend fun getMatchesByTeam(teamId: String): List<Match> =
        MatchesTable.selectAll().where { MatchesTable.teamId eq Uuid.parse(teamId) }
            .orderBy(MatchesTable.dateTime)
            .map(::toMatch)

    override suspend fun getMatchesByClub(clubId: String): List<Match> =
        MatchesTable.selectAll().where { MatchesTable.clubId eq Uuid.parse(clubId) }
            .orderBy(MatchesTable.dateTime)
            .map(::toMatch)

    override suspend fun createMatch(match: Match): Match =
        MatchesTable.insert {
            it[id] = Uuid.parse(match.id)
            it[clubId] = Uuid.parse(match.clubId)
            it[teamId] = Uuid.parse(match.teamId)
            it[opponentId] = Uuid.parse(match.opponentId)
            it[location] = match.location
            it[dateTime] = match.dateTime
            it[status] = match.status
            it[notes] = match.notes
            it[halfLengthMinutes] = match.halfLengthMinutes
            it[kickoffAt] = match.kickoffAt
            it[secondHalfStartedAt] = match.secondHalfStartedAt
        }.resultedValues
            ?.singleOrNull()
            ?.let(::toMatch)
            ?: throw DatabaseException("Failed to create match")

    override suspend fun updateMatch(match: Match): Boolean =
        MatchesTable.update({ MatchesTable.id eq Uuid.parse(match.id) }) {
            it[opponentId] = Uuid.parse(match.opponentId)
            it[location] = match.location
            it[dateTime] = match.dateTime
            it[notes] = match.notes
        } > 0

    override suspend fun updateMatchStatus(matchId: String, status: MatchStatus): Boolean =
        MatchesTable.update({ MatchesTable.id eq Uuid.parse(matchId) }) {
            it[MatchesTable.status] = status
        } > 0

    override suspend fun startMatch(
        matchId: String,
        startingPlayerIds: List<String>,
        halfLengthMinutes: Int
    ): Boolean {
        val matchUuid = Uuid.parse(matchId)
        val startingUuids = startingPlayerIds.map(Uuid::parse)

        CallUpsTable.update({ (CallUpsTable.matchId eq matchUuid) and (CallUpsTable.playerId inList startingUuids) }) {
            it[isStarting] = true
        }
        CallUpsTable.update({ (CallUpsTable.matchId eq matchUuid) and (CallUpsTable.playerId notInList startingUuids) }) {
            it[isStarting] = false
        }

        return MatchesTable.update({ MatchesTable.id eq matchUuid }) {
            it[status] = MatchStatus.LIVE
            it[MatchesTable.halfLengthMinutes] = halfLengthMinutes
            it[kickoffAt] = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        } > 0
    }

    override suspend fun markSecondHalfStarted(matchId: String, at: Instant): Boolean =
        MatchesTable.update({ MatchesTable.id eq Uuid.parse(matchId) }) {
            it[secondHalfStartedAt] = at
        } > 0

    override suspend fun getCallUpsForMatch(matchId: String): List<MatchCallUpWithPlayer> =
        (CallUpsTable innerJoin PlayersTable).selectAll()
            .where { CallUpsTable.matchId eq Uuid.parse(matchId) }
            .map {
                MatchCallUpWithPlayer(
                    callUp = toMatchCallUp(it),
                    player = toPlayer(it)
                )
            }

    override suspend fun manageCallUps(matchId: String, playerIds: List<String>): Boolean {
        val matchUuid = Uuid.parse(matchId)
        val currentPlayerIds =
            CallUpsTable.selectAll().where { CallUpsTable.matchId eq matchUuid }
                .map { it[CallUpsTable.playerId].toString() }

        val toAdd = playerIds.filter { it !in currentPlayerIds }
        val toRemove = currentPlayerIds.filter { it !in playerIds }

        if (toRemove.isNotEmpty()) {
            CallUpsTable.deleteWhere {
                (CallUpsTable.matchId eq matchUuid) and (CallUpsTable.playerId inList toRemove.map {
                    Uuid.parse(
                        it
                    )
                })
            }
        }

        if (toAdd.isNotEmpty()) {
            CallUpsTable.batchInsert(toAdd) { playerIdStr ->
                this[CallUpsTable.id] = Uuid.random()
                this[CallUpsTable.matchId] = matchUuid
                this[CallUpsTable.playerId] = Uuid.parse(playerIdStr)
                this[CallUpsTable.status] = CallUpStatus.CALLED_UP
            }
        }
        return true
    }

    override suspend fun updateCallUp(
        callUpId: String,
        status: CallUpStatus,
        notes: String?
    ): Boolean =
        CallUpsTable.update({ CallUpsTable.id eq Uuid.parse(callUpId) }) {
            it[CallUpsTable.status] = status
            it[CallUpsTable.notes] = notes
        } > 0

    override suspend fun getCallUpByMatchAndPlayer(
        matchId: String,
        playerId: String
    ): MatchCallUp? =
        CallUpsTable.selectAll()
            .where {
                (CallUpsTable.matchId eq Uuid.parse(matchId)) and (CallUpsTable.playerId eq Uuid.parse(
                    playerId
                ))
            }
            .map(::toMatchCallUp)
            .singleOrNull()

    override suspend fun getCallUpById(id: String): MatchCallUp? =
        CallUpsTable.selectAll().where { CallUpsTable.id eq Uuid.parse(id) }
            .map(::toMatchCallUp)
            .singleOrNull()

    override suspend fun getUpcomingMatchesByClub(clubId: String, limit: Int): List<Match> {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        return MatchesTable.selectAll()
            .where { (MatchesTable.clubId eq Uuid.parse(clubId)) and (MatchesTable.dateTime greaterEq now) }
            .orderBy(MatchesTable.dateTime, SortOrder.ASC)
            .limit(limit)
            .map(::toMatch)
    }

    override suspend fun getUpcomingMatchesByPlayer(playerId: String, limit: Int): List<Match> {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val playerUuid = Uuid.parse(playerId)
        return (MatchesTable innerJoin CallUpsTable).selectAll()
            .where { (CallUpsTable.playerId eq playerUuid) and (MatchesTable.dateTime greaterEq now) }
            .orderBy(MatchesTable.dateTime, SortOrder.ASC)
            .limit(limit)
            .map(::toMatch)
    }

    private fun toMatch(row: ResultRow): Match = Match(
        id = row[MatchesTable.id].toString(),
        clubId = row[MatchesTable.clubId].toString(),
        teamId = row[MatchesTable.teamId].toString(),
        opponentId = row[MatchesTable.opponentId].toString(),
        location = row[MatchesTable.location],
        dateTime = row[MatchesTable.dateTime],
        status = row[MatchesTable.status],
        notes = row[MatchesTable.notes],
        halfLengthMinutes = row[MatchesTable.halfLengthMinutes],
        kickoffAt = row[MatchesTable.kickoffAt],
        secondHalfStartedAt = row[MatchesTable.secondHalfStartedAt]
    )

    private fun toOpponent(row: ResultRow): Opponent = Opponent(
        id = row[OpponentsTable.id].toString(),
        clubId = row[OpponentsTable.clubId].toString(),
        name = row[OpponentsTable.name],
        shortName = row[OpponentsTable.shortName],
        logoUrl = row[OpponentsTable.logoUrl]
    )

    private fun toMatchCallUp(row: ResultRow): MatchCallUp = MatchCallUp(
        id = row[CallUpsTable.id].toString(),
        matchId = row[CallUpsTable.matchId].toString(),
        playerId = row[CallUpsTable.playerId].toString(),
        status = row[CallUpsTable.status],
        notes = row[CallUpsTable.notes],
        isStarting = row[CallUpsTable.isStarting]
    )

    private fun toPlayer(row: ResultRow): Player = Player(
        id = row[PlayersTable.id].toString(),
        clubId = row[PlayersTable.clubId].toString(),
        firstName = row[PlayersTable.firstName],
        lastName = row[PlayersTable.lastName],
        birthDate = row[PlayersTable.birthDate],
        shirtNumber = row[PlayersTable.shirtNumber],
        photoUrl = row[PlayersTable.photoUrl],
        active = row[PlayersTable.active]
    )
}
