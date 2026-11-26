package com.sportssoftw.squadcall.coach.domain

import com.sportssoftw.squadcall.model.CoachSpace
import com.sportssoftw.squadcall.model.CreateStaffInviteRequest
import com.sportssoftw.squadcall.model.PlansResponse
import com.sportssoftw.squadcall.model.SpacePlan
import com.sportssoftw.squadcall.model.SpaceAgeGroups
import com.sportssoftw.squadcall.model.SpaceDetails
import com.sportssoftw.squadcall.model.AddAgeGroupsRequest
import com.sportssoftw.squadcall.model.AddedAgeGroups
import com.sportssoftw.squadcall.model.StaffInvite

/** The coach's spaces on the coach server (TECHNICAL_DECISIONS §72). */
interface SpaceRepository {
    /** Every space the coach is in, their own first, with their age groups there; empty before any. */
    suspend fun getSpaces(): List<SpaceAgeGroups>

    /** One space the coach is in, for its details screen: its age groups with their teams and next games. */
    suspend fun getSpaceDetails(spaceId: String): SpaceDetails

    /**
     * Asks the coach server whether the space has room for one more age group this season, before the wizard or
     * the import opens. Returns when it has; throws the server's refusal (the plan's) when it doesn't.
     */
    suspend fun checkAgeGroupRoom(spaceId: String)

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

    /** Deletes [spaceId] with everything in it, as its owner, after their [currentPassword]. */
    suspend fun deleteSpace(spaceId: String, currentPassword: String)

    /** Leaves [spaceId] as a staff member, after their [currentPassword]: their access to its age groups goes too. */
    suspend fun leaveSpace(spaceId: String, currentPassword: String)
}

/** The owner's invite codes for an age group (`/v1/age-groups/{id}/invites`). */
interface StaffInviteRepository {
    suspend fun getInvites(ageGroupId: String): List<StaffInvite>
    // With an email in the request, the code is also emailed there.
    suspend fun createInvite(ageGroupId: String, request: CreateStaffInviteRequest = CreateStaffInviteRequest()): StaffInvite
    suspend fun revokeInvite(ageGroupId: String, code: String)
}
