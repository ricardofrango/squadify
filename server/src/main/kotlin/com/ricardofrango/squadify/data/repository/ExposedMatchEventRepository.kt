package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.MatchEvents
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchScore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid
import com.ricardofrango.squadify.util.toUuid

class ExposedMatchEventRepository : MatchEventRepository {

    override suspend fun addEvent(event: MatchEvent): MatchEvent =
        MatchEvents.insert {
            it[id] = event.id.toUuid()
            it[matchId] = event.matchId.toUuid()
            it[type] = event.type
            it[minute] = event.minute
            it[playerId] = event.playerId?.let(String::toUuid)
            it[opponentPlayerName] = event.opponentPlayerName
            it[isOpponent] = event.isOpponent
            it[createdAt] = event.createdAt
            it[period] = event.period
            it[relatedEventId] = event.relatedEventId?.toUuid()
        }.resultedValues
            ?.singleOrNull()
            ?.let(::toMatchEvent)
            ?: throw DatabaseException("Failed to add match event")

    override suspend fun getEventById(id: String): MatchEvent? =
        MatchEvents.selectAll().where { MatchEvents.id eq id.toUuid() }
            .map(::toMatchEvent)
            .singleOrNull()

    override suspend fun deleteEvent(id: String): Boolean =
        MatchEvents.deleteWhere { MatchEvents.id eq id.toUuid() } > 0

    override suspend fun deleteEventsByMatch(matchId: String) {
        MatchEvents.deleteWhere { MatchEvents.matchId eq matchId.toUuid() }
    }

    override suspend fun getEventsByMatch(matchId: String): List<MatchEvent> =
        MatchEvents.selectAll().where { MatchEvents.matchId eq matchId.toUuid() }
            .orderBy(MatchEvents.minute to SortOrder.ASC)
            .map(::toMatchEvent)

    override suspend fun getScoreByMatch(matchId: String): MatchScore {
        val events = MatchEvents.selectAll().where {
            (MatchEvents.matchId eq matchId.toUuid()) and
                    ((MatchEvents.type eq EventType.GOAL) or (MatchEvents.type eq EventType.OWN_GOAL))
        }.map(::toMatchEvent)

        return scoreOf(matchId, events)
    }

    override suspend fun getScoresByMatches(matchIds: List<String>): Map<String, MatchScore> {
        if (matchIds.isEmpty()) return emptyMap()
        val events = MatchEvents.selectAll().where {
            (MatchEvents.matchId inList matchIds.map { it.toUuid() }) and
                    ((MatchEvents.type eq EventType.GOAL) or (MatchEvents.type eq EventType.OWN_GOAL))
        }.map(::toMatchEvent).groupBy { it.matchId }
        return matchIds.associateWith { scoreOf(it, events[it].orEmpty()) }
    }

    private fun scoreOf(matchId: String, events: List<MatchEvent>): MatchScore {
        var ourScore = 0
        var opponentScore = 0

        events.forEach { event ->
            when (event.type) {
                EventType.GOAL -> {
                    if (event.isOpponent) opponentScore++ else ourScore++
                }

                EventType.OWN_GOAL -> {
                    if (event.isOpponent) ourScore++ else opponentScore++
                }

                else -> {}
            }
        }

        return MatchScore(matchId, ourScore, opponentScore)
    }

    override suspend fun getRecentEventsByPlayer(playerId: String, limit: Int): List<MatchEvent> =
        MatchEvents.selectAll()
            .where { MatchEvents.playerId eq playerId.toUuid() }
            .orderBy(MatchEvents.createdAt, SortOrder.DESC)
            .limit(limit)
            .map(::toMatchEvent)

    override suspend fun getAllEventsByPlayer(playerId: String): List<MatchEvent> =
        MatchEvents.selectAll()
            .where { MatchEvents.playerId eq playerId.toUuid() }
            .orderBy(MatchEvents.createdAt, SortOrder.DESC)
            .map(::toMatchEvent)

    private fun toMatchEvent(row: ResultRow): MatchEvent = MatchEvent(
        id = row[MatchEvents.id].toString(),
        matchId = row[MatchEvents.matchId].toString(),
        type = row[MatchEvents.type],
        minute = row[MatchEvents.minute],
        playerId = row[MatchEvents.playerId]?.toString(),
        opponentPlayerName = row[MatchEvents.opponentPlayerName],
        isOpponent = row[MatchEvents.isOpponent],
        createdAt = row[MatchEvents.createdAt],
        period = row[MatchEvents.period],
        relatedEventId = row[MatchEvents.relatedEventId]?.toString()
    )
}
