package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchLineupChange
import com.ricardofrango.squadify.model.MatchListStatus
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.NOT_PLAYED_AFTER
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid
import com.ricardofrango.squadify.data.database.MatchCallUps as CallUpsTable
import com.ricardofrango.squadify.data.database.MatchLineupChanges as LineupChangesTable
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

    override suspend fun getMatchesByOpponent(opponentId: String): List<Match> =
        MatchesTable.selectAll().where { MatchesTable.opponentId eq opponentId.toUuid() }
            .orderBy(MatchesTable.dateTime, SortOrder.DESC)
            .map(::toMatch)

    override suspend fun getMatchesByClub(clubId: String): List<Match> =
        MatchesTable.selectAll().where { MatchesTable.clubId eq clubId.toUuid() }
            .orderBy(MatchesTable.dateTime)
            .map(::toMatch)

    override suspend fun getMatchesPage(teamIds: Collection<String>, status: MatchListStatus, limit: Int, offset: Int, now: Instant): List<Match> {
        if (teamIds.isEmpty()) return emptyList()
        val order = if (status == MatchListStatus.UPCOMING) SortOrder.ASC else SortOrder.DESC
        return MatchesTable.selectAll()
            .where { matchesIn(teamIds, status, now) }
            // The id keeps matches at the same time in one order from page to page.
            .orderBy(MatchesTable.dateTime to order, MatchesTable.id to order)
            .limit(limit)
            .offset(offset.toLong())
            .map(::toMatch)
    }

    override suspend fun countMatches(teamIds: Collection<String>, status: MatchListStatus, now: Instant): Int =
        if (teamIds.isEmpty()) 0 else MatchesTable.selectAll().where { matchesIn(teamIds, status, now) }.count().toInt()

    // NOT_PLAYED: still scheduled well past kickoff (Match.isNotPlayed); UPCOMING: the rest that's
    // neither finished nor cancelled; FINISHED: finished.
    private fun matchesIn(teamIds: Collection<String>, status: MatchListStatus, now: Instant): Op<Boolean> {
        val notPlayedBefore = now - NOT_PLAYED_AFTER
        val notPlayed = (MatchesTable.status eq MatchStatus.SCHEDULED) and (MatchesTable.dateTime lessEq notPlayedBefore)
        return (MatchesTable.teamId inList teamIds.map { it.toUuid() }) and when (status) {
            MatchListStatus.NOT_PLAYED -> notPlayed
            MatchListStatus.UPCOMING -> (MatchesTable.status notInList listOf(MatchStatus.FINISHED, MatchStatus.CANCELLED)) and not(notPlayed)
            MatchListStatus.FINISHED -> MatchesTable.status eq MatchStatus.FINISHED
        }
    }

    override suspend fun hasMatches(teamIds: Collection<String>): Boolean =
        teamIds.isNotEmpty() && !MatchesTable.selectAll()
            .where { MatchesTable.teamId inList teamIds.map { it.toUuid() } }
            .limit(1)
            .empty()

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
            it[venue] = match.venue
            it[MatchesTable.teamSize] = match.teamSize
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
            it[venue] = match.venue
            it[MatchesTable.teamSize] = match.teamSize
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
        halfLengthMinutes: Int,
        formation: String
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
            it[MatchesTable.formation] = formation
            it[startingFormation] = formation
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

    override suspend fun markEnteredAfterGame(matchId: String, halfLengthMinutes: Int, halves: Int): Boolean =
        MatchesTable.update({ MatchesTable.id eq matchId.toUuid() }) {
            it[status] = MatchStatus.FINISHED
            it[enteredAfterGame] = true
            it[MatchesTable.halfLengthMinutes] = halfLengthMinutes
            it[firstHalfEndMinute] = if (halves == 2) halfLengthMinutes else null
            it[fullTimeMinute] = halfLengthMinutes * halves
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

    override suspend fun setLineup(matchId: String, formation: String, slots: Map<Int, String>): Boolean {
        val matchUuid = matchId.toUuid()
        // Every slot cleared first: one player per position (unique_match_pitch_slot) would otherwise
        // refuse two players swapping places.
        CallUpsTable.update({ CallUpsTable.matchId eq matchUuid }) { it[pitchSlot] = null }
        slots.forEach { (slot, playerId) ->
            CallUpsTable.update({ (CallUpsTable.matchId eq matchUuid) and (CallUpsTable.playerId eq playerId.toUuid()) }) {
                it[pitchSlot] = slot
            }
        }
        return MatchesTable.update({ MatchesTable.id eq matchUuid }) { it[MatchesTable.formation] = formation } > 0
    }

    override suspend fun addLineupChange(matchId: String, change: MatchLineupChange) {
        LineupChangesTable.insert {
            it[id] = Uuid.random()
            it[LineupChangesTable.matchId] = matchId.toUuid()
            it[minute] = change.minute
            it[period] = change.period
            it[formation] = change.formation
            it[slots] = Json.encodeToString(change.slots)
            it[createdAt] = change.createdAt
        }
    }

    override suspend fun getLineupChanges(matchId: String): List<MatchLineupChange> =
        LineupChangesTable.selectAll().where { LineupChangesTable.matchId eq matchId.toUuid() }
            .orderBy(LineupChangesTable.createdAt)
            .map {
                MatchLineupChange(
                    minute = it[LineupChangesTable.minute],
                    period = it[LineupChangesTable.period],
                    formation = it[LineupChangesTable.formation],
                    slots = Json.decodeFromString<Map<Int, String>>(it[LineupChangesTable.slots]),
                    createdAt = it[LineupChangesTable.createdAt]
                )
            }

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
        venue = row[MatchesTable.venue],
        halfLengthMinutes = row[MatchesTable.halfLengthMinutes],
        kickoffAt = row[MatchesTable.kickoffAt],
        secondHalfStartedAt = row[MatchesTable.secondHalfStartedAt],
        pausedAt = row[MatchesTable.pausedAt],
        firstHalfEndMinute = row[MatchesTable.firstHalfEndMinute],
        fullTimeMinute = row[MatchesTable.fullTimeMinute],
        formation = row[MatchesTable.formation],
        startingFormation = row[MatchesTable.startingFormation],
        enteredAfterGame = row[MatchesTable.enteredAfterGame],
        teamSize = row[MatchesTable.teamSize]
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
        isStarting = row[CallUpsTable.isStarting],
        pitchSlot = row[CallUpsTable.pitchSlot]
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
