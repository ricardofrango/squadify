package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.TrainingSessionsTable
import com.ricardofrango.squadify.domain.repository.SessionContentCounts
import com.ricardofrango.squadify.domain.repository.StoredTrainingSession
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant

class ExposedTrainingSessionRepository : TrainingSessionRepository {

    override suspend fun getByAgeGroup(ageGroupId: String): List<StoredTrainingSession> =
        TrainingSessionsTable.selectAll().where { TrainingSessionsTable.ageGroupId eq ageGroupId.toUuid() }
            .map(::toStored)
            .sortedWith(compareBy({ it.session.date }, { it.session.startTime }))

    override suspend fun getById(id: String): StoredTrainingSession? =
        TrainingSessionsTable.selectAll().where { TrainingSessionsTable.id eq id.toUuid() }.map(::toStored).singleOrNull()

    override suspend fun insertAll(sessions: List<TrainingSession>) {
        val now = Clock.System.now()
        TrainingSessionsTable.batchInsert(sessions) { s ->
            this[TrainingSessionsTable.id] = s.id.toUuid()
            this[TrainingSessionsTable.ageGroupId] = s.ageGroupId.toUuid()
            this[TrainingSessionsTable.date] = s.date
            this[TrainingSessionsTable.startTime] = s.startTime
            this[TrainingSessionsTable.endTime] = s.endTime
            this[TrainingSessionsTable.location] = s.location
            this[TrainingSessionsTable.notes] = s.notes
            this[TrainingSessionsTable.createdAt] = now
        }
    }

    override suspend fun update(session: TrainingSession): Boolean =
        TrainingSessionsTable.update({ TrainingSessionsTable.id eq session.id.toUuid() }) {
            it[date] = session.date
            it[startTime] = session.startTime
            it[endTime] = session.endTime
            it[location] = session.location
            it[notes] = session.notes
        } > 0

    override suspend fun setCancelled(id: String, at: Instant?, by: String?): Boolean =
        TrainingSessionsTable.update({ TrainingSessionsTable.id eq id.toUuid() }) {
            it[cancelledAt] = at
            it[cancelledBy] = by?.toUuid()
        } > 0

    override suspend fun deleteAll(ids: List<String>) {
        if (ids.isEmpty()) return
        TrainingSessionsTable.deleteWhere { TrainingSessionsTable.id inList ids.map { it.toUuid() } }
    }

    override suspend fun contentCounts(ids: List<String>): Map<String, SessionContentCounts> =
        ids.associateWith { SessionContentCounts(exercises = 0, ratings = 0) }

    private fun toStored(row: ResultRow) = StoredTrainingSession(
        session = TrainingSession(
            id = row[TrainingSessionsTable.id].toString(),
            ageGroupId = row[TrainingSessionsTable.ageGroupId].toString(),
            date = row[TrainingSessionsTable.date],
            startTime = row[TrainingSessionsTable.startTime],
            endTime = row[TrainingSessionsTable.endTime],
            location = row[TrainingSessionsTable.location],
            notes = row[TrainingSessionsTable.notes],
            cancelledAt = row[TrainingSessionsTable.cancelledAt]
        ),
        cancelledBy = row[TrainingSessionsTable.cancelledBy]?.toString()
    )
}
