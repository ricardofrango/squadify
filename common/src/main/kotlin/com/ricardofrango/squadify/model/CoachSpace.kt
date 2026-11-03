package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Squadify for Coaches (REQUIREMENTS §5c, TECHNICAL_DECISIONS §72): a coach's space is a club row the
// coach owns, never shown as a club. These are the coach server's first-setup request and responses.

/**
 * The first setup, right after signing up: the space's name and sport, and its first age group and
 * team. The season is picked from the date by the server.
 */
@Serializable
data class SetUpSpaceRequest(
    val name: String,
    val sport: SportType,
    val ageGroup: AgeGroupItemRequest,
    val teamName: String,
    // Null: the age group's team size.
    val teamSize: Int? = null
)

/** What the first setup created. */
@Serializable
data class SpaceSetup(
    val space: Club,
    val season: Season,
    val ageGroup: AgeGroup,
    val team: Team
)

/**
 * A code the owner shares so someone joins an age group of the space as staff (REQUIREMENTS §5c).
 * Single-use, and valid until [expiresAt]. [code] is shown as "K7Q-4MZ"; it's accepted with or
 * without the dash, in any case.
 */
@Serializable
data class StaffInvite(
    val code: String,
    val ageGroupId: String,
    val ageGroupName: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    // The address the code was emailed to; null when it was made to share by hand.
    val email: String? = null
)

/**
 * `POST /v1/age-groups/{id}/invites`: with no [email], a code to share by hand, as before; with one, the
 * code is also emailed there, in [language] ("en" or "pt", the owner's app language; English otherwise).
 */
@Serializable
data class CreateStaffInviteRequest(val email: String? = null, val language: String? = null)

/**
 * A space's plan (REQUIREMENTS §5c), for the whole space. Free: one age group, the owner alone, no stats.
 * Basic: up to 3 age groups and 3 people on each, the owner included, with stats. Pro: no limits,
 * following games live, and training (coming). Going down a plan deletes nothing: the first age groups
 * created and the first staff to join stay, the others are locked. What each plan allows is the coach
 * server's to decide (CoachAccessPolicy).
 */
@Serializable
enum class SpacePlan { FREE, BASIC, PRO }

/**
 * What a plan has (`GET /v1/plans`, the coach server), as the coach server decides it: the app shows it
 * and never decides a plan rule itself, so the plans can change without a new app. [features] are every
 * feature the plan has ([PlanFeature]'s keys; an app skips one it doesn't know). The limits: null, no limit.
 */
@Serializable
data class PlanDetails(
    val plan: SpacePlan,
    val features: List<String>,
    val ageGroupLimit: Int? = null,
    // The owner included.
    val peoplePerAgeGroupLimit: Int? = null
)

/** The keys of [PlanDetails.features]. Strings, not an enum, so an older app reads a plan with a new one. */
object PlanFeature {
    const val LIVE_GAME = "LIVE_GAME"
    const val CALENDAR = "CALENDAR"
    const val HISTORY = "HISTORY"
    const val STATS = "STATS"
    // Live scores in the match lists, a game's screen updating as someone else records it, and the live
    // moments' notifications.
    const val FOLLOWING_LIVE = "FOLLOWING_LIVE"
    const val TRAINING = "TRAINING"
}

/** A space in the platform admin's list (`GET /v1/admin/spaces`, the coach server): its owner and plan. */
@Serializable
data class AdminSpace(val id: String, val name: String, val ownerEmail: String?, val plan: SpacePlan)

/** `PUT /v1/admin/spaces/{id}/plan`: a platform admin sets a space's plan, until payments. */
@Serializable
data class SetSpacePlanRequest(val plan: SpacePlan)

/**
 * One space the signed-in coach is in, with the age groups they have there: every one in the space they
 * own, the ones they're staff on in someone else's (REQUIREMENTS §5c).
 */
@Serializable
data class SpaceAgeGroups(
    val space: Club,
    val isOwner: Boolean,
    val ageGroups: List<AgeGroup>,
    val plan: SpacePlan = SpacePlan.FREE,
    // Of [ageGroups], the ones the plan locks for this coach: over the space's limit, or with them over
    // the age group's people limit. Listed, but not opened.
    val lockedAgeGroupIds: List<String> = emptyList(),
    // The owner's: how many age groups the space has this season (My Space's Plan card), and how many more
    // the plan allows (Create age group); null: no limit. 0 and null for staff.
    val ageGroupsThisSeason: Int = 0,
    val ageGroupsLeft: Int? = null
)

/** A space the signed-in coach belongs to, and whether they own it. */
@Serializable
data class CoachSpace(
    val space: Club,
    val isOwner: Boolean
)

/**
 * What deleting the signed-in coach's account takes with it, for the warning (REQUIREMENTS §5c): their own
 * space and what's in it, or null when they own none.
 */
@Serializable
data class AccountDeletionSummary(
    val ownSpaceName: String? = null,
    val ageGroups: Int = 0,
    val players: Int = 0,
    val matches: Int = 0,
    // Everyone else in the space, who loses access to it.
    val staff: Int = 0
)

/** Deleting the account: the current password, as for every other account change. */
@Serializable
data class DeleteAccountRequest(val currentPassword: String)
