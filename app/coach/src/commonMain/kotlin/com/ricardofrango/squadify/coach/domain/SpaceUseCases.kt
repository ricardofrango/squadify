package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.StaffInvite

class GetSpacesUseCase(private val spaceRepository: SpaceRepository) {
    suspend operator fun invoke(): List<SpaceAgeGroups> = spaceRepository.getSpaces()
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

class CreateStaffInviteUseCase(private val staffInviteRepository: StaffInviteRepository) {
    suspend operator fun invoke(ageGroupId: String): StaffInvite = staffInviteRepository.createInvite(ageGroupId)
}

class RevokeStaffInviteUseCase(private val staffInviteRepository: StaffInviteRepository) {
    suspend operator fun invoke(ageGroupId: String, code: String) = staffInviteRepository.revokeInvite(ageGroupId, JoinCode.normalize(code))
}
