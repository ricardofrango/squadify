package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.TrainingPlansTable
import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.model.TrainingPhase
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.util.toUuid
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock

class ExposedTrainingPlanRepository : TrainingPlanRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getByAgeGroup(ageGroupId: String): List<TrainingPlan> =
        TrainingPlansTable.selectAll().where { TrainingPlansTable.ageGroupId eq ageGroupId.toUuid() }
            .map(::toPlan)

    override suspend fun getByAgeGroups(ageGroupIds: List<String>): List<TrainingPlan> {
        if (ageGroupIds.isEmpty()) return emptyList()
        return TrainingPlansTable.selectAll().where { TrainingPlansTable.ageGroupId inList ageGroupIds.map { it.toUuid() } }
            .map(::toPlan)
    }

    override suspend fun save(plan: TrainingPlan, updatedBy: String): TrainingPlan {
        val now = Clock.System.now()
        val existing = TrainingPlansTable.selectAll().where {
            (TrainingPlansTable.ageGroupId eq plan.ageGroupId.toUuid()) and
                (TrainingPlansTable.level eq plan.level) and
                (TrainingPlansTable.periodStart eq plan.periodStart)
        }.singleOrNull()

        if (existing == null) {
            TrainingPlansTable.insert {
                it[id] = plan.id.toUuid()
                it[ageGroupId] = plan.ageGroupId.toUuid()
                it[level] = plan.level
                it[periodStart] = plan.periodStart
                it[focus] = plan.focus
                it[objectives] = json.encodeToString(plan.objectives)
                it[notes] = plan.notes
                it[intensity] = plan.intensity
                it[phases] = json.encodeToString(plan.phases)
                it[updatedAt] = now
                it[this.updatedBy] = updatedBy.toUuid()
            }
            return plan.copy(updatedAt = now)
        }

        val id = existing[TrainingPlansTable.id]
        TrainingPlansTable.update({ TrainingPlansTable.id eq id }) {
            it[focus] = plan.focus
            it[objectives] = json.encodeToString(plan.objectives)
            it[notes] = plan.notes
            it[intensity] = plan.intensity
            it[phases] = json.encodeToString(plan.phases)
            it[updatedAt] = now
            it[this.updatedBy] = updatedBy.toUuid()
        }
        return plan.copy(id = id.toString(), updatedAt = now)
    }

    private fun toPlan(row: ResultRow) = TrainingPlan(
        id = row[TrainingPlansTable.id].toString(),
        ageGroupId = row[TrainingPlansTable.ageGroupId].toString(),
        level = row[TrainingPlansTable.level],
        periodStart = row[TrainingPlansTable.periodStart],
        focus = row[TrainingPlansTable.focus],
        objectives = json.decodeFromString<List<String>>(row[TrainingPlansTable.objectives]),
        notes = row[TrainingPlansTable.notes],
        intensity = row[TrainingPlansTable.intensity],
        phases = json.decodeFromString<List<TrainingPhase>>(row[TrainingPlansTable.phases]),
        updatedAt = row[TrainingPlansTable.updatedAt]
    )
}
