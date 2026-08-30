package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.AgeGroups
import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.AgeGroup
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ExposedAgeGroupRepository : AgeGroupRepository {

    override suspend fun getAgeGroupsByClub(clubId: String): List<AgeGroup> = dbQuery {
        AgeGroups.selectAll()
            .where { AgeGroups.clubId eq Uuid.parse(clubId) }
            .orderBy(AgeGroups.birthYear, SortOrder.DESC)
            .map(::toAgeGroup)
    }

    override suspend fun createAgeGroup(ageGroup: AgeGroup): AgeGroup = dbQuery {
        AgeGroups.insert {
            it[id] = Uuid.parse(ageGroup.id)
            it[clubId] = Uuid.parse(ageGroup.clubId)
            it[seasonId] = Uuid.parse(ageGroup.seasonId)
            it[name] = ageGroup.name
            it[birthYear] = ageGroup.birthYear
        }.resultedValues?.singleOrNull()?.let(::toAgeGroup)
            ?: throw DatabaseException("Failed to create age group")
    }

    override suspend fun getAgeGroupById(id: String): AgeGroup? = dbQuery {
        AgeGroups.selectAll().where { AgeGroups.id eq Uuid.parse(id) }
            .map(::toAgeGroup)
            .singleOrNull()
    }

    override suspend fun updateAgeGroup(ageGroup: AgeGroup): Boolean = dbQuery {
        AgeGroups.update({ AgeGroups.id eq Uuid.parse(ageGroup.id) }) {
            it[name] = ageGroup.name
            it[birthYear] = ageGroup.birthYear
        } > 0
    }

    override suspend fun findByDetails(clubId: String, seasonId: String, birthYear: Int): AgeGroup? = dbQuery {
        AgeGroups.selectAll()
            .where { 
                (AgeGroups.clubId eq Uuid.parse(clubId)) and 
                (AgeGroups.seasonId eq Uuid.parse(seasonId)) and 
                (AgeGroups.birthYear eq birthYear) 
            }
            .map(::toAgeGroup)
            .singleOrNull()
    }

    private fun toAgeGroup(row: ResultRow): AgeGroup = AgeGroup(
        id = row[AgeGroups.id].toString(),
        clubId = row[AgeGroups.clubId].toString(),
        seasonId = row[AgeGroups.seasonId].toString(),
        name = row[AgeGroups.name],
        birthYear = row[AgeGroups.birthYear]
    )
}
