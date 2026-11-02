package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.CreateStaffInviteRequest
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.domain.config.OwnSpace
import kotlinx.coroutines.flow.MutableStateFlow
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.StaffInvite

/** Every space the coach is in; each load also keeps [OwnSpacePlan] up to date. */
class GetSpacesUseCase(private val spaceRepository: SpaceRepository, private val ownSpacePlan: OwnSpacePlan) {
    suspend operator fun invoke(): List<SpaceAgeGroups> =
        spaceRepository.getSpaces().also { spaces ->
            ownSpacePlan.space.value = spaces.firstOrNull { it.isOwner }?.let { OwnSpace(it.space.id, it.plan, it.ageGroupsThisSeason) }
        }
}

/**
 * The space the coach owns, its plan and this season's age groups, as last loaded (null: they own none,
 * or nothing loaded yet). The shared screens' plan messages read it: See plans on a stats lock, the
 * Members tab, Invite staff and Create age groups when the plan has no room.
 */
class OwnSpacePlan {
    val space = MutableStateFlow<OwnSpace?>(null)
}

class SetUpSpaceUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(request: SetUpSpaceRequest): SpaceSetup = spaceRepository.setUpSpace(request)
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
