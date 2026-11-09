package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.CreateStaffInviteRequest
import com.ricardofrango.squadify.model.PlansResponse
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpaceDetails
import com.ricardofrango.squadify.model.AddAgeGroupsRequest
import com.ricardofrango.squadify.model.AddedAgeGroups
import com.ricardofrango.squadify.model.StaffInvite

/** The coach's spaces on the coach server (TECHNICAL_DECISIONS §72). */
interface SpaceRepository {
    /** Every space the coach is in, their own first, with their age groups there; empty before any. */
    suspend fun getSpaces(): List<SpaceAgeGroups>

    /** One space the coach is in, for its details screen: its age groups with their teams and next games. */
    suspend fun getSpaceDetails(spaceId: String): SpaceDetails

    /** The age-group wizard: one club's age groups with their teams, in their own space (made the first time). */
    suspend fun addAgeGroups(request: AddAgeGroupsRequest): AddedAgeGroups

    /** An age group's crest, the club it plays for; returns its path. */
    suspend fun uploadAgeGroupCrest(ageGroupId: String, bytes: ByteArray): String

    /** An image the server serves at [path] (e.g. a crest already used in the space), to give it to another. */
    suspend fun imageBytes(path: String): ByteArray

    /** Every plan, cheapest first, with what it has in the app's language, and the coach's own (the coach server's). */
    suspend fun getPlans(): PlansResponse

    /**
     * Asks for [plan] for the space they own, until payments: set at once by a local server, by a platform
     * admin otherwise. Returns the plans after it, as [getPlans].
     */
    suspend fun requestPlan(plan: SpacePlan): PlansResponse

    /** A Google Play purchase, for the server to check with Google and set the plan from. Returns the plans after it. */
    suspend fun verifyGooglePurchase(productId: String, purchaseToken: String): PlansResponse


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
