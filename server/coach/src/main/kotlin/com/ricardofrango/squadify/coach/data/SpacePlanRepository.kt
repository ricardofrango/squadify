package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.data.database.AgeGroupStaff
import com.ricardofrango.squadify.data.database.AgeGroups
import com.ricardofrango.squadify.data.database.ClubMembers
import com.ricardofrango.squadify.data.database.Clubs
import com.ricardofrango.squadify.data.database.Users
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Instant

/** A space, for the platform admin's list: its owner's email and its plan. */
data class SpaceSummary(val id: String, val name: String, val ownerEmail: String?, val plan: SpacePlan)

/**
 * The spaces' plans (`space_plans`, REQUIREMENTS §5c) and the order the plan's limits go by: when each
 * age group was created and when each staff member joined. Called inside the callers' transactions.
 */
interface SpacePlanRepository {
    /** [SpacePlan.FREE] for a space with no plan set. */
    suspend fun planOf(clubId: String): SpacePlan
    suspend fun setPlan(clubId: String, plan: SpacePlan, at: Instant)
    /** The space's age groups as (id, season id), the first created first. */
    suspend fun ageGroupsInCreationOrder(clubId: String): List<Pair<String, String>>
    /** [ageGroupId]'s staff, the first to join first (when they used their code); anyone without one last. */
    suspend fun staffInJoinOrder(ageGroupId: String): List<String>
    /** Every space, by name. */
    suspend fun spaces(): List<SpaceSummary>
}

class ExposedSpacePlanRepository : SpacePlanRepository {
    override suspend fun planOf(clubId: String): SpacePlan =
        SpacePlans.selectAll().where { SpacePlans.clubId eq clubId.toUuid() }.singleOrNull()?.get(SpacePlans.plan) ?: SpacePlan.FREE

    override suspend fun setPlan(clubId: String, plan: SpacePlan, at: Instant) {
        SpacePlans.upsert {
            it[SpacePlans.clubId] = clubId.toUuid()
            it[SpacePlans.plan] = plan
            it[updatedAt] = at
        }
    }

    override suspend fun ageGroupsInCreationOrder(clubId: String): List<Pair<String, String>> =
        AgeGroups.select(AgeGroups.id, AgeGroups.seasonId)
            .where { AgeGroups.clubId eq clubId.toUuid() }
            .orderBy(AgeGroups.createdAt to SortOrder.ASC, AgeGroups.id to SortOrder.ASC)
            .map { it[AgeGroups.id].toString() to it[AgeGroups.seasonId].toString() }

    override suspend fun staffInJoinOrder(ageGroupId: String): List<String> {
        val ageGroup = ageGroupId.toUuid()
        val staff = AgeGroupStaff.select(AgeGroupStaff.userId).where { AgeGroupStaff.ageGroupId eq ageGroup }
            .map { it[AgeGroupStaff.userId].toString() }.distinct()
        // Someone taken off and back on has joined again: their latest code counts.
        val joined = StaffInvites.select(StaffInvites.acceptedBy, StaffInvites.acceptedAt)
            .where { (StaffInvites.ageGroupId eq ageGroup) and StaffInvites.acceptedBy.isNotNull() }
            .groupBy({ it[StaffInvites.acceptedBy].toString() }, { it[StaffInvites.acceptedAt] })
            .mapValues { (_, times) -> times.filterNotNull().maxOrNull() }
        return staff.sortedWith(compareBy(nullsLast()) { userId: String -> joined[userId] }.thenBy { it })
    }

    override suspend fun spaces(): List<SpaceSummary> {
        val plans = SpacePlans.selectAll().associate { it[SpacePlans.clubId].toString() to it[SpacePlans.plan] }
        val owners = (ClubMembers innerJoin Users).select(ClubMembers.clubId, Users.email)
            .where { ClubMembers.role eq ClubRole.CLUB_ADMIN }
            .associate { it[ClubMembers.clubId].toString() to it[Users.email] }
        return Clubs.select(Clubs.id, Clubs.name).orderBy(Clubs.name to SortOrder.ASC).map {
            val id = it[Clubs.id].toString()
            SpaceSummary(id, it[Clubs.name], owners[id], plans[id] ?: SpacePlan.FREE)
        }
    }
}
