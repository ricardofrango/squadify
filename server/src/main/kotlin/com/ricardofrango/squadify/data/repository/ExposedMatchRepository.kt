package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
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
import com.ricardofrango.squadify.data.database.MatchCallUps as CallUpsTable
import com.ricardofrango.squadify.data.database.Matches as MatchesTable
import com.ricardofrango.squadify.data.database.Opponents as OpponentsTable
import com.ricardofrango.squadify.data.database.Players as PlayersTable
import com.ricardofrango.squadify.util.toUuid

class ExposedMatchRepository : MatchRepository {

    override suspend fun getMatchById(id: String): Match? =
        MatchesTable.selectAll().where { MatchesTable.id eq id.toUuid() }
            .map(::toMatch)
            .singleOrNull()

    override suspend fun getMatchesByTeam(teamId: String): List<Match> =
        MatchesTable.selectAll().where { MatchesTable.teamId eq teamId.toUuid() }
            .orderBy(MatchesTable.dateTime)
            .map(::toMatch)

    override suspend fun getMatchesByClub(clubId: String): List<Match> =
        MatchesTable.selectAll().where { MatchesTable.clubId eq clubId.toUuid() }
            .orderBy(MatchesTable.dateTime)
            .map(::toMatch)

    override suspend fun createMatch(match: Match): Match =
        MatchesTable.insert {
            it[id] = match.id.toUuid()
            it[clubId] = match.clubId.toUuid()
            it[teamId] = match.teamId.toUuid()
            it[opponentId] = match.opponentId.toUuid()
            it[location] = match.location
            it[dateTime] = match.dateTime
            it[status] = match.status
            it[notes] = match.notes
            it[matchType] = match.type
            it[halves] = match.halves
            it[halfLengthMinutes] = match.halfLengthMinutes
            it[kickoffAt] = match.kickoffAt
            it[secondHalfStartedAt] = match.secondHalfStartedAt
        }.resultedValues
            ?.singleOrNull()
            ?.let(::toMatch)
            ?: throw DatabaseException("Failed to create match")

    override suspend fun updateMatch(match: Match): Boolean =
        MatchesTable.update({ MatchesTable.id eq match.id.toUuid() }) {
            it[opponentId] = match.opponentId.toUuid()
            it[location] = match.location
            it[dateTime] = match.dateTime
            it[notes] = match.notes
            it[matchType] = match.type
            it[halves] = match.halves
        } > 0

    override suspend fun updateMatchStatus(matchId: String, status: MatchStatus): Boolean =
        MatchesTable.update({ MatchesTable.id eq matchId.toUuid() }) {
            it[MatchesTable.status] = status
        } > 0

    override suspend fun deleteMatch(matchId: String): Boolean =
        MatchesTable.deleteWhere { MatchesTable.id eq matchId.toUuid() } > 0

    override suspend fun startMatch(
        matchId: String,
        startingPlayerIds: List<String>,
        halfLengthMinutes: Int
    ): Boolean {
        val matchUuid = matchId.toUuid()
        val startingUuids = startingPlayerIds.map(String::toUuid)

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
        MatchesTable.update({ MatchesTable.id eq matchId.toUuid() }) {
            it[secondHalfStartedAt] = at
            it[pausedAt] = null
        } > 0

    override suspend fun markFirstHalfEnded(matchId: String, minute: Int): Boolean =
        MatchesTable.update({ MatchesTable.id eq matchId.toUuid() }) {
            it[firstHalfEndMinute] = minute
            it[pausedAt] = null
        } > 0

    override suspend fun markFullTime(matchId: String, minute: Int): Boolean =
        MatchesTable.update({ MatchesTable.id eq matchId.toUuid() }) {
            it[fullTimeMinute] = minute
            it[pausedAt] = null
        } > 0

    override suspend fun pauseMatch(matchId: String, at: Instant): Boolean =
        MatchesTable.update({ MatchesTable.id eq matchId.toUuid() }) {
            it[pausedAt] = at
        } > 0

    override suspend fun resumeMatch(matchId: String, kickoffAt: Instant, secondHalfStartedAt: Instant?): Boolean =
        MatchesTable.update({ MatchesTable.id eq matchId.toUuid() }) {
            it[pausedAt] = null
            it[MatchesTable.kickoffAt] = kickoffAt
            it[MatchesTable.secondHalfStartedAt] = secondHalfStartedAt
        } > 0

    override suspend fun adjustMatchClock(matchId: String, kickoffAt: Instant, secondHalfStartedAt: Instant?): Boolean =
        MatchesTable.update({ MatchesTable.id eq matchId.toUuid() }) {
            it[MatchesTable.kickoffAt] = kickoffAt
            it[MatchesTable.secondHalfStartedAt] = secondHalfStartedAt
        } > 0

    override suspend fun getCallUpsForMatch(matchId: String): List<MatchCallUpWithPlayer> =
        (CallUpsTable innerJoin PlayersTable).selectAll()
            .where { CallUpsTable.matchId eq matchId.toUuid() }
            .map {
                MatchCallUpWithPlayer(
                    callUp = toMatchCallUp(it),
                    player = toPlayer(it)
                )
            }

    override suspend fun countCallUpsByPlayer(playerId: String): Long =
        CallUpsTable.selectAll().where { CallUpsTable.playerId eq playerId.toUuid() }.count()

    override suspend fun getCallUpMatchIdsByPlayer(playerId: String): List<String> =
        CallUpsTable.selectAll().where { CallUpsTable.playerId eq playerId.toUuid() }
            .map { it[CallUpsTable.matchId].toString() }

    override suspend fun manageCallUps(matchId: String, playerIds: List<String>): Boolean {
        val matchUuid = matchId.toUuid()
        val currentPlayerIds =
            CallUpsTable.selectAll().where { CallUpsTable.matchId eq matchUuid }
                .map { it[CallUpsTable.playerId].toString() }

        val toAdd = playerIds.filter { it !in currentPlayerIds }
        val toRemove = currentPlayerIds.filter { it !in playerIds }

        if (toRemove.isNotEmpty()) {
            CallUpsTable.deleteWhere {
                (CallUpsTable.matchId eq matchUuid) and (CallUpsTable.playerId inList toRemove.map {
                    it.toUuid()
                })
            }
        }

        if (toAdd.isNotEmpty()) {
            CallUpsTable.batchInsert(toAdd) { playerIdStr ->
                this[CallUpsTable.id] = Uuid.random()
                this[CallUpsTable.matchId] = matchUuid
                this[CallUpsTable.playerId] = playerIdStr.toUuid()
                this[CallUpsTable.status] = CallUpStatus.CALLED_UP
            }
        }
        return true
    }

    override suspend fun getCallUpByMatchAndPlayer(
        matchId: String,
        playerId: String
    ): MatchCallUp? =
        CallUpsTable.selectAll()
            .where {
                (CallUpsTable.matchId eq matchId.toUuid()) and (CallUpsTable.playerId eq playerId.toUuid())
            }
            .map(::toMatchCallUp)
            .singleOrNull()

    override suspend fun getUpcomingMatchesByClub(clubId: String, limit: Int): List<Match> {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        return MatchesTable.selectAll()
            .where { (MatchesTable.clubId eq clubId.toUuid()) and (MatchesTable.dateTime greaterEq now) }
            .orderBy(MatchesTable.dateTime, SortOrder.ASC)
            .limit(limit)
            .map(::toMatch)
    }

    override suspend fun getUpcomingMatchesByPlayer(playerId: String, limit: Int): List<Match> {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val playerUuid = playerId.toUuid()
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
        type = row[MatchesTable.matchType],
        halves = row[MatchesTable.halves],
        halfLengthMinutes = row[MatchesTable.halfLengthMinutes],
        kickoffAt = row[MatchesTable.kickoffAt],
        secondHalfStartedAt = row[MatchesTable.secondHalfStartedAt],
        pausedAt = row[MatchesTable.pausedAt],
        firstHalfEndMinute = row[MatchesTable.firstHalfEndMinute],
        fullTimeMinute = row[MatchesTable.fullTimeMinute]
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
        active = row[PlayersTable.active],
        position = row[PlayersTable.position],
        heightCm = row[PlayersTable.heightCm],
        weightKg = row[PlayersTable.weightKg],
        emergencyContact = row[PlayersTable.emergencyContact]
    )
}
