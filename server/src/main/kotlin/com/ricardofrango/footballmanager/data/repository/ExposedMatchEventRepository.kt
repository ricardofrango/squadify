package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.data.database.MatchEvents
import com.ricardofrango.footballmanager.domain.repository.MatchEventRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.EventType
import com.ricardofrango.footballmanager.model.MatchEvent
import com.ricardofrango.footballmanager.model.MatchScore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

class ExposedMatchEventRepository : MatchEventRepository {

    override suspend fun addEvent(event: MatchEvent): MatchEvent = dbQuery {
        MatchEvents.insert {
            it[id] = Uuid.parse(event.id)
            it[matchId] = Uuid.parse(event.matchId)
            it[type] = event.type
            it[minute] = event.minute
            it[playerId] = event.playerId?.let(Uuid::parse)
            it[opponentPlayerName] = event.opponentPlayerName
            it[isOpponent] = event.isOpponent
            it[createdAt] = event.createdAt
        }.resultedValues?.singleOrNull()?.let(::toMatchEvent)
            ?: throw DatabaseException("Failed to add match event")
    }

    override suspend fun getEventsByMatch(matchId: String): List<MatchEvent> = dbQuery {
        MatchEvents.selectAll().where { MatchEvents.matchId eq Uuid.parse(matchId) }
            .orderBy(MatchEvents.minute to org.jetbrains.exposed.v1.core.SortOrder.ASC)
            .map(::toMatchEvent)
    }

    override suspend fun getScoreByMatch(matchId: String): MatchScore = dbQuery {
        val events = MatchEvents.selectAll().where {
            (MatchEvents.matchId eq Uuid.parse(matchId)) and
                    ((MatchEvents.type eq EventType.GOAL) or (MatchEvents.type eq EventType.OWN_GOAL))
        }.map(::toMatchEvent)

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

        MatchScore(matchId, ourScore, opponentScore)
    }

    override suspend fun getRecentEventsByPlayer(playerId: String, limit: Int): List<MatchEvent> = dbQuery {
        MatchEvents.selectAll()
            .where { MatchEvents.playerId eq Uuid.parse(playerId) }
            .orderBy(MatchEvents.createdAt, SortOrder.DESC)
            .limit(limit)
            .map(::toMatchEvent)
    }

    private fun toMatchEvent(row: ResultRow): MatchEvent = MatchEvent(
        id = row[MatchEvents.id].toString(),
        matchId = row[MatchEvents.matchId].toString(),
        type = row[MatchEvents.type],
        minute = row[MatchEvents.minute],
        playerId = row[MatchEvents.playerId]?.toString(),
        opponentPlayerName = row[MatchEvents.opponentPlayerName],
        isOpponent = row[MatchEvents.isOpponent],
        createdAt = row[MatchEvents.createdAt]
    )
}
