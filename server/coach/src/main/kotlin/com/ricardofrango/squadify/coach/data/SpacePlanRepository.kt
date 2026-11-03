package com.ricardofrango.squadify.coach.data

import com.ricardofrango.squadify.data.database.AgeGroupStaff
import com.ricardofrango.squadify.data.database.AgeGroups
import com.ricardofrango.squadify.data.database.ClubMembers
import com.ricardofrango.squadify.data.database.Clubs
import com.ricardofrango.squadify.data.database.Users
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.coach.domain.PlanLimits
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Instant

/** A space, for the platform admin's list: its owner's email, its plan and the plan asked for. */
data class SpaceSummary(val id: String, val name: String, val ownerEmail: String?, val plan: SpacePlan, val requestedPlan: SpacePlan? = null)

/**
 * The spaces' plans (`space_plans`, REQUIREMENTS §5c), the plans their owners asked for
 * (`plan_requests`) and the order the plan's limits go by: when each
 * age group was created and when each staff member joined. Called inside the callers' transactions.
 */
interface SpacePlanRepository {
    /** Free ([PlanLimits.FREE]) for a space with no plan set. */
    suspend fun planOf(clubId: String): SpacePlan
    /** Sets the plan and clears any request for one. */
    suspend fun setPlan(clubId: String, plan: SpacePlan, at: Instant)
    /** The plan the space's owner asked for and hasn't got yet; null: none. */
    suspend fun requestedPlan(clubId: String): SpacePlan?
    /** Records [plan] as asked for by [userId], in place of an earlier request. */
    suspend fun requestPlan(clubId: String, plan: SpacePlan, userId: String, at: Instant)
    /** The space's age groups as (id, season id), the first created first. */
    suspend fun ageGroupsInCreationOrder(clubId: String): List<Pair<String, String>>
    /** [ageGroupId]'s staff, the first to join first (when they used their code); anyone without one last. */
    suspend fun staffInJoinOrder(ageGroupId: String): List<String>
    /** Every space, by name. */
    suspend fun spaces(): List<SpaceSummary>
}

class ExposedSpacePlanRepository : SpacePlanRepository {
    override suspend fun planOf(clubId: String): SpacePlan =
        SpacePlans.selectAll().where { SpacePlans.clubId eq clubId.toUuid() }.singleOrNull()?.let { SpacePlan(it[SpacePlans.plan]) } ?: PlanLimits.FREE

    override suspend fun setPlan(clubId: String, plan: SpacePlan, at: Instant) {
        SpacePlans.upsert {
            it[SpacePlans.clubId] = clubId.toUuid()
            it[SpacePlans.plan] = plan.id
            it[updatedAt] = at
        }
        PlanRequests.deleteWhere { PlanRequests.clubId eq clubId.toUuid() }
    }

    override suspend fun requestedPlan(clubId: String): SpacePlan? =
        PlanRequests.selectAll().where { PlanRequests.clubId eq clubId.toUuid() }.singleOrNull()?.let { SpacePlan(it[PlanRequests.plan]) }

    override suspend fun requestPlan(clubId: String, plan: SpacePlan, userId: String, at: Instant) {
        PlanRequests.upsert {
            it[PlanRequests.clubId] = clubId.toUuid()
            it[PlanRequests.plan] = plan.id
            it[requestedBy] = userId.toUuid()
            it[requestedAt] = at
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
        val plans = SpacePlans.selectAll().associate { it[SpacePlans.clubId].toString() to SpacePlan(it[SpacePlans.plan]) }
        val requests = PlanRequests.selectAll().associate { it[PlanRequests.clubId].toString() to SpacePlan(it[PlanRequests.plan]) }
        val owners = (ClubMembers innerJoin Users).select(ClubMembers.clubId, Users.email)
            .where { ClubMembers.role eq ClubRole.CLUB_ADMIN }
            .associate { it[ClubMembers.clubId].toString() to it[Users.email] }
        return Clubs.select(Clubs.id, Clubs.name).orderBy(Clubs.name to SortOrder.ASC).map {
            val id = it[Clubs.id].toString()
            SpaceSummary(id, it[Clubs.name], owners[id], plans[id] ?: PlanLimits.FREE, requests[id])
        }
    }
}
