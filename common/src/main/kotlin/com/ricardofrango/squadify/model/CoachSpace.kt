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
    val peoplePerAgeGroupLimit: Int? = null,
    // How it's sold in the stores, one a billing period; none for Free.
    val storeProducts: List<StoreProduct> = emptyList()
) {
    fun has(feature: String): Boolean = features.any { it.key == feature }
}

/** One feature of a plan: its key ([PlanFeature]) and how the Plans screen lists it, written by the server. */
@Serializable
data class PlanFeatureLine(val key: String, val text: String)

/** How often a subscription is charged. */
@Serializable
enum class BillingPeriod { MONTHLY, YEARLY }

/**
 * A plan's subscription for one [period] in the stores, as the coach server names it: on Google Play a
 * subscription ([googleProductId], one a plan) and one of its base plans ([googleBasePlanId]). The prices
 * are the store's, read by the app.
 */
@Serializable
data class StoreProduct(val period: BillingPeriod, val googleProductId: String, val googleBasePlanId: String)

/**
 * How the coach changes plan (`PlansResponse.changeBy`), the server's choice: [INSTANT] on a local server
 * (`POST /v1/plans/request` sets it there and then); [REQUEST] while a deployed server can't check store
 * purchases (the request waits for a platform admin); [STORE] with them (the store's payment sheet, then
 * `POST /v1/plans/purchases/google`). An app without the store's billing (iOS, for now) asks instead.
 */
@Serializable
enum class PlanChange { INSTANT, REQUEST, STORE }

/**
 * `GET /v1/plans`: every plan, cheapest first, the plan of the space the coach owns (null: they own none)
 * and the plan they asked for and haven't got yet ([requestedPlan], null: none). With a store subscription
 * going down at its renewal, [pendingPlan] is the plan from [pendingFrom] (REQUIREMENTS §5c). For the
 * store: the period of the subscription the plan comes from ([currentPeriod], null: none), and the id the
 * app tags a purchase with ([storeAccountId], the owner's; the server only accepts their own purchases).
 */
@Serializable
data class PlansResponse(
    val plans: List<PlanDetails>,
    val currentPlan: SpacePlan? = null,
    val requestedPlan: SpacePlan? = null,
    val changeBy: PlanChange = PlanChange.REQUEST,
    val pendingPlan: SpacePlan? = null,
    val pendingFrom: Instant? = null,
    val currentPeriod: BillingPeriod? = null,
    val storeAccountId: String? = null
)

/**
 * `POST /v1/plans/purchases/google`: a Google Play subscription bought (or changed) in the app, for the
 * server to check with Google and set the plan of the buyer's space. Answered with the plans, like `GET /v1/plans`.
 */
@Serializable
data class GooglePurchaseRequest(val productId: String, val purchaseToken: String)

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
    // The plan's ads: shown (Free, never in a live game) or none (the paid plans); whether a space shows
    // them is SpaceAgeGroups.showsAds.
    const val ADS = "ADS"
    const val NO_ADS = "NO_ADS"
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
    val ageGroupsLeft: Int? = null,
    // Whether the app shows ads on this space's screens: its plan's, decided by the coach server (Free).
    // False from an older server.
    val showsAds: Boolean = false,
    // The space owner's name, for a space they're staff in ("de João Pereira"); null from an older server.
    val ownerName: String? = null,
    // How many opponents the space has, one list for all its age groups.
    val opponents: Int = 0
)

/**
 * One space as its details screen shows it (`GET /v1/spaces/{id}`, the coach server): the space, the
 * current season's name ("26/27"), its plan, and the age groups the coach has there (all of them for the
 * owner, their own for staff), each with its teams and next game.
 */
@Serializable
data class SpaceDetails(
    val space: Club,
    val isOwner: Boolean,
    val ownerName: String? = null,
    val plan: SpacePlan,
    val seasonName: String? = null,
    // The current season's id: the wizard marks the birth years a club already has in it.
    val seasonId: String? = null,
    val ageGroups: List<SpaceAgeGroup>,
    // The owner's: how many more age groups the plan allows this season; null: no limit, or staff.
    val ageGroupsLeft: Int? = null,
    val showsAds: Boolean = false
)

/** An age group on the space's details: its teams' names, its next game, and whether the plan locks it. */
@Serializable
data class SpaceAgeGroup(
    val ageGroup: AgeGroup,
    val teams: List<String>,
    val nextMatch: SpaceNextMatch? = null,
    val locked: Boolean = false
)

/**
 * The age-group wizard (`POST /v1/space/age-groups`, the coach server): age groups for the club they play
 * for, named "{clubName} - {name}" ("Povoense - Sub 11"), each with [NewAgeGroup.teams] teams named "A",
 * "B"…, in the coach's own space and the current season, all at once. A coach with no space yet gets one,
 * [spaceName] ("Espaço de Ana Silva") in [sport]. The crest is uploaded to each age group afterwards.
 */
@Serializable
data class AddAgeGroupsRequest(
    val clubName: String,
    val ageGroups: List<NewAgeGroup>,
    val spaceName: String? = null,
    val sport: SportType? = null
)

/** One age group of [AddAgeGroupsRequest]: its own name ("Sub 11"), birth years, a-side size and how many teams. */
@Serializable
data class NewAgeGroup(
    val name: String,
    val startYear: Int,
    val endYear: Int,
    val teamSize: Int? = null,
    val teams: Int = 1
)

/** What the wizard created: the space (new or not), its new age groups and how many teams. */
@Serializable
data class AddedAgeGroups(val space: Club, val ageGroups: List<AgeGroup>, val teams: Int)

/** An age group's next game, for its row: when and against whom. */
@Serializable
data class SpaceNextMatch(val matchId: String, val dateTime: Instant, val opponentName: String)

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

/** Deleting a space (its owner only): the owner's current password, as for the account. */
@Serializable
data class DeleteSpaceRequest(val currentPassword: String)

/** Leaving a space (a staff member only): their own current password. */
@Serializable
data class LeaveSpaceRequest(val currentPassword: String)
