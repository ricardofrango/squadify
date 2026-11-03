package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.CreateStaffInviteRequest
import com.ricardofrango.squadify.model.PlansResponse
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.StaffInvite

/** The coach's spaces on the coach server (TECHNICAL_DECISIONS §72). */
interface SpaceRepository {
    /** Every space the coach is in, their own first, with their age groups there; empty before any. */
    suspend fun getSpaces(): List<SpaceAgeGroups>

    /** Every plan, cheapest first, with what it has in the app's language, and the coach's own (the coach server's). */
    suspend fun getPlans(): PlansResponse

    /** Asks for [plan] for the space they own, until payments; a platform admin sets it. */
    suspend fun requestPlan(plan: SpacePlan)

    /** Their own space, with its first age group and team. 409 once they own one. */
    suspend fun setUpSpace(request: SetUpSpaceRequest): SpaceSetup

    /** Joins the code's age group as staff, returning its space. */
    suspend fun acceptInvite(code: String): CoachSpace
}

/** The owner's invite codes for an age group (`/v1/age-groups/{id}/invites`). */
interface StaffInviteRepository {
    suspend fun getInvites(ageGroupId: String): List<StaffInvite>
    // With an email in the request, the code is also emailed there.
    suspend fun createInvite(ageGroupId: String, request: CreateStaffInviteRequest = CreateStaffInviteRequest()): StaffInvite
    suspend fun revokeInvite(ageGroupId: String, code: String)
}
