package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.AgeGroups
import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.AgeGroup
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

class ExposedAgeGroupRepository : AgeGroupRepository {

    override suspend fun getAgeGroupsByClub(clubId: String): List<AgeGroup> = dbQuery {
        AgeGroups.selectAll().where { AgeGroups.clubId eq Uuid.parse(clubId) }
            .map(::toAgeGroup)
    }

    override suspend fun createAgeGroup(ageGroup: AgeGroup): AgeGroup = dbQuery {
        AgeGroups.insert {
            it[id] = Uuid.parse(ageGroup.id)
            it[clubId] = Uuid.parse(ageGroup.clubId)
            it[seasonId] = Uuid.parse(ageGroup.seasonId)
            it[name] = ageGroup.name
            it[sortOrder] = ageGroup.sortOrder
        }.resultedValues?.singleOrNull()?.let(::toAgeGroup)
            ?: throw DatabaseException("Failed to create age group")
    }

    override suspend fun getAgeGroupById(id: String): AgeGroup? = dbQuery {
        AgeGroups.selectAll().where { AgeGroups.id eq Uuid.parse(id) }
            .map(::toAgeGroup)
            .singleOrNull()
    }

    private fun toAgeGroup(row: ResultRow): AgeGroup = AgeGroup(
        id = row[AgeGroups.id].toString(),
        clubId = row[AgeGroups.clubId].toString(),
        seasonId = row[AgeGroups.seasonId].toString(),
        name = row[AgeGroups.name],
        sortOrder = row[AgeGroups.sortOrder]
    )
}
