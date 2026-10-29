package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.TrainingExercisesTable
import com.ricardofrango.squadify.data.database.TrainingSessionExercisesTable
import com.ricardofrango.squadify.domain.repository.TrainingExerciseRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.TrainingExercise
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock

class ExposedTrainingExerciseRepository : TrainingExerciseRepository {

    override suspend fun getByAgeGroup(ageGroupId: String): List<TrainingExercise> {
        val exercises = TrainingExercisesTable.selectAll().where { TrainingExercisesTable.ageGroupId eq ageGroupId.toUuid() }.map(::toExercise)
        if (exercises.isEmpty()) return exercises
        val used = TrainingSessionExercisesTable.selectAll()
            .where { TrainingSessionExercisesTable.exerciseId inList exercises.map { it.id.toUuid() } }
            .groupingBy { it[TrainingSessionExercisesTable.exerciseId].toString() }
            .eachCount()
        return exercises.map { it.copy(usedCount = used[it.id] ?: 0) }
    }

    override suspend fun getById(id: String): TrainingExercise? =
        TrainingExercisesTable.selectAll().where { TrainingExercisesTable.id eq id.toUuid() }.map(::toExercise).singleOrNull()
            ?.let { e -> e.copy(usedCount = TrainingSessionExercisesTable.selectAll().where { TrainingSessionExercisesTable.exerciseId eq id.toUuid() }.count().toInt()) }

    override suspend fun create(exercise: TrainingExercise): TrainingExercise =
        TrainingExercisesTable.insert {
            it[id] = exercise.id.toUuid()
            it[ageGroupId] = exercise.ageGroupId.toUuid()
            it[name] = exercise.name
            it[description] = exercise.description
            it[durationMinutes] = exercise.durationMinutes
            it[objectives] = exercise.objectives
            it[createdAt] = Clock.System.now()
        }.resultedValues?.singleOrNull()?.let(::toExercise) ?: throw DatabaseException("Failed to create exercise")

    override suspend fun update(exercise: TrainingExercise): Boolean =
        TrainingExercisesTable.update({ TrainingExercisesTable.id eq exercise.id.toUuid() }) {
            it[name] = exercise.name
            it[description] = exercise.description
            it[durationMinutes] = exercise.durationMinutes
            it[objectives] = exercise.objectives
        } > 0

    override suspend fun getForSession(sessionId: String): List<TrainingExercise> {
        val order = TrainingSessionExercisesTable.selectAll()
            .where { TrainingSessionExercisesTable.sessionId eq sessionId.toUuid() }
            .orderBy(TrainingSessionExercisesTable.position, SortOrder.ASC)
            .map { it[TrainingSessionExercisesTable.exerciseId] }
        if (order.isEmpty()) return emptyList()
        val byId = TrainingExercisesTable.selectAll().where { TrainingExercisesTable.id inList order }.map(::toExercise).associateBy { it.id }
        return order.mapNotNull { byId[it.toString()] }
    }

    override suspend fun setForSession(sessionId: String, exerciseIds: List<String>) {
        TrainingSessionExercisesTable.deleteWhere { TrainingSessionExercisesTable.sessionId eq sessionId.toUuid() }
        TrainingSessionExercisesTable.batchInsert(exerciseIds.withIndex().toList()) { (i, id) ->
            this[TrainingSessionExercisesTable.sessionId] = sessionId.toUuid()
            this[TrainingSessionExercisesTable.exerciseId] = id.toUuid()
            this[TrainingSessionExercisesTable.position] = i
        }
    }

    private fun toExercise(row: ResultRow) = TrainingExercise(
        id = row[TrainingExercisesTable.id].toString(),
        ageGroupId = row[TrainingExercisesTable.ageGroupId].toString(),
        name = row[TrainingExercisesTable.name],
        description = row[TrainingExercisesTable.description],
        durationMinutes = row[TrainingExercisesTable.durationMinutes],
        objectives = row[TrainingExercisesTable.objectives]
    )
}
