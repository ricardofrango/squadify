package com.ricardofrango.clubmanager.data.repository

import com.ricardofrango.clubmanager.data.database.AgeGroupStaff
import com.ricardofrango.clubmanager.data.database.AgeGroups
import com.ricardofrango.clubmanager.data.database.Users
import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.exception.DatabaseException
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.AgeGroupStaffMember
import com.ricardofrango.clubmanager.model.TeamRole
import com.ricardofrango.clubmanager.model.AgeGroupStaff as AgeGroupStaffModel
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ExposedAgeGroupRepository : AgeGroupRepository {

    override suspend fun getAgeGroupsByClub(clubId: String): List<AgeGroup> {
        return AgeGroups.selectAll()
            .where { AgeGroups.clubId eq Uuid.parse(clubId) }
            .orderBy(AgeGroups.birthYear, SortOrder.DESC)
            .map(::toAgeGroup)
    }

    override suspend fun createAgeGroup(ageGroup: AgeGroup): AgeGroup {
        return AgeGroups.insert {
            it[id] = Uuid.parse(ageGroup.id)
            it[clubId] = Uuid.parse(ageGroup.clubId)
            it[seasonId] = Uuid.parse(ageGroup.seasonId)
            it[name] = ageGroup.name
            it[birthYear] = ageGroup.birthYear
            it[teamSize] = ageGroup.teamSize
        }.resultedValues?.singleOrNull()?.let(::toAgeGroup)
            ?: throw DatabaseException("Failed to create age group")
    }

    override suspend fun createAgeGroups(ageGroups: List<AgeGroup>): List<AgeGroup> {
        return AgeGroups.batchInsert(ageGroups) { ageGroup ->
            this[AgeGroups.id] = Uuid.parse(ageGroup.id)
            this[AgeGroups.clubId] = Uuid.parse(ageGroup.clubId)
            this[AgeGroups.seasonId] = Uuid.parse(ageGroup.seasonId)
            this[AgeGroups.name] = ageGroup.name
            this[AgeGroups.birthYear] = ageGroup.birthYear
            this[AgeGroups.teamSize] = ageGroup.teamSize
        }.map(::toAgeGroup)
    }

    override suspend fun getAgeGroupById(id: String): AgeGroup? {
        return AgeGroups.selectAll().where { AgeGroups.id eq Uuid.parse(id) }
            .map(::toAgeGroup)
            .singleOrNull()
    }

    override suspend fun updateAgeGroup(ageGroup: AgeGroup): Boolean {
        return AgeGroups.update({ AgeGroups.id eq Uuid.parse(ageGroup.id) }) {
            it[name] = ageGroup.name
            it[birthYear] = ageGroup.birthYear
        } > 0
    }

    override suspend fun findByDetails(clubId: String, seasonId: String, birthYear: Int): AgeGroup? {
        return AgeGroups.selectAll()
            .where { 
                (AgeGroups.clubId eq Uuid.parse(clubId)) and 
                (AgeGroups.seasonId eq Uuid.parse(seasonId)) and 
                (AgeGroups.birthYear eq birthYear) 
            }
            .map(::toAgeGroup)
            .singleOrNull()
    }

    override suspend fun findByClubAndSeason(clubId: String, seasonId: String): List<AgeGroup> {
        return AgeGroups.selectAll()
            .where {
                (AgeGroups.clubId eq Uuid.parse(clubId)) and
                (AgeGroups.seasonId eq Uuid.parse(seasonId))
            }
            .map(::toAgeGroup)
    }

    override suspend fun getUserRoleInAgeGroup(userId: String, ageGroupId: String): TeamRole? =
        AgeGroupStaff.selectAll()
            .where {
                (AgeGroupStaff.userId eq Uuid.parse(userId)) and
                (AgeGroupStaff.ageGroupId eq Uuid.parse(ageGroupId))
            }
            .map { it[AgeGroupStaff.role] }
            .maxByOrNull { it.level }

    override suspend fun addStaffToAgeGroup(staff: List<AgeGroupStaffModel>): Boolean =
        AgeGroupStaff.batchInsert(staff) { s ->
            this[AgeGroupStaff.id] = Uuid.parse(s.id)
            this[AgeGroupStaff.ageGroupId] = Uuid.parse(s.ageGroupId)
            this[AgeGroupStaff.userId] = Uuid.parse(s.userId)
            this[AgeGroupStaff.role] = s.role
        }.size == staff.size

    override suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember> =
        (AgeGroupStaff innerJoin Users).selectAll()
            .where { AgeGroupStaff.ageGroupId eq Uuid.parse(ageGroupId) }
            .map {
                AgeGroupStaffMember(
                    userId = it[Users.id].toString(),
                    firstName = it[Users.firstName],
                    lastName = it[Users.lastName],
                    role = it[AgeGroupStaff.role],
                    avatarUrl = it[Users.avatarUrl]
                )
            }

    override suspend fun countCoachesByClub(clubId: String): Long =
        (AgeGroupStaff innerJoin AgeGroups).selectAll()
            .where { (AgeGroups.clubId eq Uuid.parse(clubId)) and (AgeGroupStaff.role eq TeamRole.COACH) }
            .map { it[AgeGroupStaff.userId] }
            .distinct()
            .size.toLong()

    private fun toAgeGroup(row: ResultRow): AgeGroup = AgeGroup(
        id = row[AgeGroups.id].toString(),
        clubId = row[AgeGroups.clubId].toString(),
        seasonId = row[AgeGroups.seasonId].toString(),
        name = row[AgeGroups.name],
        birthYear = row[AgeGroups.birthYear],
        teamSize = row[AgeGroups.teamSize]
    )
}
