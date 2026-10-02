package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.AgeGroupStaff
import com.ricardofrango.squadify.data.database.AgeGroups
import com.ricardofrango.squadify.data.database.Users
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.AgeGroupStaff as AgeGroupStaffModel
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid
import com.ricardofrango.squadify.util.toUuid

class ExposedAgeGroupRepository : AgeGroupRepository {

    override suspend fun getAgeGroupsByClub(clubId: String): List<AgeGroup> {
        return AgeGroups.selectAll()
            .where { AgeGroups.clubId eq clubId.toUuid() }
            .orderBy(AgeGroups.endYear, SortOrder.DESC)
            .map(::toAgeGroup)
    }

    override suspend fun createAgeGroup(ageGroup: AgeGroup): AgeGroup {
        return AgeGroups.insert {
            it[id] = ageGroup.id.toUuid()
            it[clubId] = ageGroup.clubId.toUuid()
            it[seasonId] = ageGroup.seasonId.toUuid()
            it[name] = ageGroup.name
            it[startYear] = ageGroup.startYear
            it[endYear] = ageGroup.endYear
            it[teamSize] = ageGroup.teamSize
        }.resultedValues?.singleOrNull()?.let(::toAgeGroup)
            ?: throw DatabaseException("Failed to create age group")
    }

    override suspend fun createAgeGroups(ageGroups: List<AgeGroup>): List<AgeGroup> {
        return AgeGroups.batchInsert(ageGroups) { ageGroup ->
            this[AgeGroups.id] = ageGroup.id.toUuid()
            this[AgeGroups.clubId] = ageGroup.clubId.toUuid()
            this[AgeGroups.seasonId] = ageGroup.seasonId.toUuid()
            this[AgeGroups.name] = ageGroup.name
            this[AgeGroups.startYear] = ageGroup.startYear
            this[AgeGroups.endYear] = ageGroup.endYear
            this[AgeGroups.teamSize] = ageGroup.teamSize
        }.map(::toAgeGroup)
    }

    override suspend fun getAgeGroupById(id: String): AgeGroup? {
        return AgeGroups.selectAll().where { AgeGroups.id eq id.toUuid() }
            .map(::toAgeGroup)
            .singleOrNull()
    }

    override suspend fun updateAgeGroup(ageGroup: AgeGroup): Boolean {
        return AgeGroups.update({ AgeGroups.id eq ageGroup.id.toUuid() }) {
            it[name] = ageGroup.name
            it[startYear] = ageGroup.startYear
            it[endYear] = ageGroup.endYear
            it[teamSize] = ageGroup.teamSize
        } > 0
    }

    override suspend fun findByDetails(clubId: String, seasonId: String, startYear: Int, endYear: Int): AgeGroup? {
        return AgeGroups.selectAll()
            .where {
                (AgeGroups.clubId eq clubId.toUuid()) and
                (AgeGroups.seasonId eq seasonId.toUuid()) and
                (AgeGroups.startYear eq startYear) and
                (AgeGroups.endYear eq endYear)
            }
            .map(::toAgeGroup)
            .singleOrNull()
    }

    override suspend fun findByClubAndSeason(clubId: String, seasonId: String): List<AgeGroup> {
        return AgeGroups.selectAll()
            .where {
                (AgeGroups.clubId eq clubId.toUuid()) and
                (AgeGroups.seasonId eq seasonId.toUuid())
            }
            .map(::toAgeGroup)
    }

    override suspend fun getUserRoleInAgeGroup(userId: String, ageGroupId: String): TeamRole? =
        AgeGroupStaff.selectAll()
            .where {
                (AgeGroupStaff.userId eq userId.toUuid()) and
                (AgeGroupStaff.ageGroupId eq ageGroupId.toUuid())
            }
            .map { it[AgeGroupStaff.role] }
            .maxByOrNull { it.level }

    override suspend fun addStaffToAgeGroup(staff: List<AgeGroupStaffModel>): Boolean =
        AgeGroupStaff.batchInsert(staff) { s ->
            this[AgeGroupStaff.id] = s.id.toUuid()
            this[AgeGroupStaff.ageGroupId] = s.ageGroupId.toUuid()
            this[AgeGroupStaff.userId] = s.userId.toUuid()
            this[AgeGroupStaff.role] = s.role
        }.size == staff.size

    override suspend fun removeStaffFromClub(clubId: String, userId: String): Int {
        val ageGroupIds = AgeGroups.select(AgeGroups.id).where { AgeGroups.clubId eq clubId.toUuid() }.map { it[AgeGroups.id] }
        if (ageGroupIds.isEmpty()) return 0
        return AgeGroupStaff.deleteWhere { (AgeGroupStaff.userId eq userId.toUuid()) and (AgeGroupStaff.ageGroupId inList ageGroupIds) }
    }

    override suspend fun getAgeGroupStaff(ageGroupId: String): List<AgeGroupStaffMember> =
        (AgeGroupStaff innerJoin Users).selectAll()
            .where { AgeGroupStaff.ageGroupId eq ageGroupId.toUuid() }
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
            .where { (AgeGroups.clubId eq clubId.toUuid()) and (AgeGroupStaff.role eq TeamRole.COACH) }
            .map { it[AgeGroupStaff.userId] }
            .distinct()
            .size.toLong()

    private fun toAgeGroup(row: ResultRow): AgeGroup = AgeGroup(
        id = row[AgeGroups.id].toString(),
        clubId = row[AgeGroups.clubId].toString(),
        seasonId = row[AgeGroups.seasonId].toString(),
        name = row[AgeGroups.name],
        startYear = row[AgeGroups.startYear],
        endYear = row[AgeGroups.endYear],
        teamSize = row[AgeGroups.teamSize]
    )
}
