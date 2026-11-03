package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
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
 * A space's plan (REQUIREMENTS §5c), for the whole space, by its id ("FREE", "BASIC", "PRO" today; sent
 * as the bare string). Not a fixed list: the coach server decides which plans there are and what each
 * has (`GET /v1/plans`), so a plan can be added or changed without a new app. Going down a plan deletes
 * nothing: the first age groups created and the first staff to join stay, the others are locked.
 */
@Serializable
@JvmInline
value class SpacePlan(val id: String)

/**
 * A plan as the coach server decides it (`GET /v1/plans`): its [name] (the same in every language),
 * every feature it has ([features], each a [PlanFeatureLine] in the language asked for) and its limits
 * (null: no limit). The app shows it as it comes and decides no plan rule itself.
 */
@Serializable
data class PlanDetails(
    val plan: SpacePlan,
    val name: String,
    val features: List<PlanFeatureLine>,
    val ageGroupLimit: Int? = null,
    // The owner included.
    val peoplePerAgeGroupLimit: Int? = null
) {
    fun has(feature: String): Boolean = features.any { it.key == feature }
}

/** One feature of a plan: its key ([PlanFeature]) and how the Plans screen lists it, written by the server. */
@Serializable
data class PlanFeatureLine(val key: String, val text: String)

/**
 * `GET /v1/plans`: every plan, cheapest first, the plan of the space the coach owns (null: they own none)
 * and the plan they asked for and haven't got yet ([requestedPlan], null: none).
 */
@Serializable
data class PlansResponse(val plans: List<PlanDetails>, val currentPlan: SpacePlan? = null, val requestedPlan: SpacePlan? = null)

/**
 * `POST /v1/plans/request`: the owner asks for another plan for their space, until payments. A platform
 * admin is told and sets it; the request stays until then.
 */
@Serializable
data class RequestPlanRequest(val plan: SpacePlan)

/**
 * The keys of [PlanFeatureLine]. Strings, not an enum, so an older app reads a plan with a feature it
 * doesn't know (and lists it, from its text). The app only asks about [STATS], for the stats lock's wording.
 */
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

/**
 * A space in the platform admin's list (`GET /v1/admin/spaces`, the coach server): its owner, its plan
 * and the plan its owner asked for (null: none).
 */
@Serializable
data class AdminSpace(val id: String, val name: String, val ownerEmail: String?, val plan: SpacePlan, val requestedPlan: SpacePlan? = null)

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
    val plan: SpacePlan,
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
