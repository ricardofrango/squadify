package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.ClubRole

// Club settings → Members and Member details (designs 34, 35).

class GetClubMembersOverviewUseCase(private val clubRepository: ClubRepository) {
    suspend operator fun invoke(clubId: String): ClubMembersOverview = clubRepository.getClubMembersOverview(clubId)
}

class UpdateClubMemberRoleUseCase(private val clubRepository: ClubRepository) {
    suspend operator fun invoke(clubId: String, userId: String, role: ClubRole) = clubRepository.updateClubMemberRole(clubId, userId, role)
}

class RemoveClubMemberUseCase(private val clubRepository: ClubRepository) {
    suspend operator fun invoke(clubId: String, userId: String) = clubRepository.removeClubMember(clubId, userId)
}

class ResendClubInviteUseCase(private val clubRepository: ClubRepository) {
    suspend operator fun invoke(clubId: String, inviteId: String) = clubRepository.resendClubInvite(clubId, inviteId)
}

class CancelClubInviteUseCase(private val clubRepository: ClubRepository) {
    suspend operator fun invoke(clubId: String, inviteId: String) = clubRepository.cancelClubInvite(clubId, inviteId)
}
