package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.CreateStaffInviteRequest
import com.ricardofrango.squadify.model.PlansResponse
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpaceDetails
import com.ricardofrango.squadify.model.AddAgeGroupsRequest
import com.ricardofrango.squadify.model.AddedAgeGroups
import com.ricardofrango.squadify.domain.config.OwnSpace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import com.ricardofrango.squadify.model.StaffInvite

/** Every space the coach is in; each load also keeps [OwnSpacePlan] up to date, the plans included. */
class GetSpacesUseCase(
    private val spaceRepository: SpaceRepository,
    private val ownSpacePlan: OwnSpacePlan,
    private val loadPlansUseCase: LoadPlansUseCase,
    private val coachAds: CoachAds
) {
    suspend operator fun invoke(): List<SpaceAgeGroups> =
        spaceRepository.getSpaces().also { spaces ->
            ownSpacePlan.space.value = spaces.firstOrNull { it.isOwner }
                ?.let { OwnSpace(it.space.id, it.plan, it.ageGroupsThisSeason, it.ageGroupsLeft) }
            coachAds.remember(spaces)
            // Best effort: the last plans loaded stay when they can't be fetched now.
            try {
                loadPlansUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
}

/** One space the coach is in, for its details screen (its age groups' teams and next games). */
class GetSpaceDetailsUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(spaceId: String): SpaceDetails = spaceRepository.getSpaceDetails(spaceId)
}

/** What the age-group wizard made, and whether every age group got its crest (the crests come after). */
data class AgeGroupsAdded(val added: AddedAgeGroups, val crestSaved: Boolean)

/**
 * The age-group wizard: creates one club's age groups and their teams (the space too, the first time),
 * then gives each its crest, best effort: [crest], the picked image, or the one at [crestPath], an age
 * group of the same club's.
 */
class AddAgeGroupsUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(request: AddAgeGroupsRequest, crest: ByteArray? = null, crestPath: String? = null): AgeGroupsAdded {
        val added = spaceRepository.addAgeGroups(request)
        val bytes = try {
            crest ?: crestPath?.let { spaceRepository.imageBytes(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AgeGroupsAdded(added, crestSaved = false)
        } ?: return AgeGroupsAdded(added, crestSaved = true)
        var saved = true
        added.ageGroups.forEach { ageGroup ->
            try {
                spaceRepository.uploadAgeGroupCrest(ageGroup.id, bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saved = false
            }
        }
        return AgeGroupsAdded(added, saved)
    }
}

/**
 * Every plan with what it has and the coach's own, as the coach server decides them, into
 * [OwnSpacePlan.plans]; the features written in the app's language.
 */
class LoadPlansUseCase(private val spaceRepository: SpaceRepository, private val ownSpacePlan: OwnSpacePlan) {
    suspend operator fun invoke() {
        ownSpacePlan.plans.value = spaceRepository.getPlans()
    }
}

/**
 * Asks the coach server for [plan] for the space the coach owns, until payments: a local server sets it at
 * once, a deployed one waits for a platform admin. The plans it sends back replace the ones loaded, and the
 * spaces are loaded again (best effort) for the plan's limits elsewhere. Whether the plan itself changed.
 */
class RequestPlanUseCase(
    private val spaceRepository: SpaceRepository,
    private val ownSpacePlan: OwnSpacePlan,
    private val getSpacesUseCase: GetSpacesUseCase
) {
    suspend operator fun invoke(plan: SpacePlan): Boolean {
        val before = ownSpacePlan.plans.value?.currentPlan
        val plans = spaceRepository.requestPlan(plan)
        ownSpacePlan.plans.value = plans
        val changed = plans.currentPlan != before
        if (changed) {
            try {
                getSpacesUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
        return changed
    }
}

/**
 * A subscription bought (or changed) in Google Play, sent to the coach server, which checks it with
 * Google and sets the plan. The plans it answers with replace the ones loaded, and the spaces are loaded
 * again (best effort) when the plan changed. Whether it did.
 */
class SaveStorePurchaseUseCase(
    private val spaceRepository: SpaceRepository,
    private val ownSpacePlan: OwnSpacePlan,
    private val getSpacesUseCase: GetSpacesUseCase
) {
    suspend operator fun invoke(productId: String, purchaseToken: String): Boolean {
        val before = ownSpacePlan.plans.value?.currentPlan
        val plans = spaceRepository.verifyGooglePurchase(productId, purchaseToken)
        ownSpacePlan.plans.value = plans
        val changed = plans.currentPlan != before
        if (changed) {
            try {
                getSpacesUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
        return changed
    }
}

/**
 * The space the coach owns, its plan and this season's age groups, as last loaded (null: they own none,
 * or nothing loaded yet), and every plan as the server sends them, with the coach's own ([plans], null until loaded). The
 * shared screens' plan messages read them: See plans on a stats lock, the Members tab, Invite staff and
 * Create age groups when the plan has no room, and the Plans screen.
 */
class OwnSpacePlan {
    val space = MutableStateFlow<OwnSpace?>(null)
    val plans = MutableStateFlow<PlansResponse?>(null)
}

class JoinWithCodeUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(code: String): CoachSpace = spaceRepository.acceptInvite(JoinCode.normalize(code))
}

class GetStaffInvitesUseCase(private val staffInviteRepository: StaffInviteRepository) {
    suspend operator fun invoke(ageGroupId: String): List<StaffInvite> = staffInviteRepository.getInvites(ageGroupId)
}

/** A code to share by hand, or with an [email] also sent there, in [language] (the app's, "en" or "pt"). */
class CreateStaffInviteUseCase(private val staffInviteRepository: StaffInviteRepository) {
    suspend operator fun invoke(ageGroupId: String, email: String? = null, language: String? = null): StaffInvite =
        staffInviteRepository.createInvite(ageGroupId, CreateStaffInviteRequest(email?.trim(), language))
}

class RevokeStaffInviteUseCase(private val staffInviteRepository: StaffInviteRepository) {
    suspend operator fun invoke(ageGroupId: String, code: String) = staffInviteRepository.revokeInvite(ageGroupId, JoinCode.normalize(code))
}
