package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.TrainingRatingsTable
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.model.TrainingRating
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Clock

class ExposedTrainingRatingRepository : TrainingRatingRepository {

    override suspend fun getForSession(sessionId: String): List<TrainingRating> =
        getForSessions(listOf(sessionId))[sessionId].orEmpty()

    override suspend fun getForSessions(sessionIds: List<String>): Map<String, List<TrainingRating>> {
        if (sessionIds.isEmpty()) return emptyMap()
        return TrainingRatingsTable.selectAll()
            .where { TrainingRatingsTable.sessionId inList sessionIds.map { it.toUuid() } }
            .groupBy(
                { it[TrainingRatingsTable.sessionId].toString() },
                { TrainingRating(it[TrainingRatingsTable.playerId].toString(), it[TrainingRatingsTable.ratingTenths] / 10.0) }
            )
    }

    override suspend fun save(sessionId: String, changes: Map<String, Int?>, updatedBy: String) {
        val cleared = changes.filterValues { it == null }.keys.map { it.toUuid() }
        if (cleared.isNotEmpty()) {
            TrainingRatingsTable.deleteWhere { (TrainingRatingsTable.sessionId eq sessionId.toUuid()) and (TrainingRatingsTable.playerId inList cleared) }
        }
        val now = Clock.System.now()
        val set = changes.mapNotNull { (player, tenths) -> tenths?.let { player to it } }
        TrainingRatingsTable.batchUpsert(set) { (player, tenths) ->
            this[TrainingRatingsTable.sessionId] = sessionId.toUuid()
            this[TrainingRatingsTable.playerId] = player.toUuid()
            this[TrainingRatingsTable.ratingTenths] = tenths
            this[TrainingRatingsTable.updatedAt] = now
            this[TrainingRatingsTable.updatedBy] = updatedBy.toUuid()
        }
    }
}
