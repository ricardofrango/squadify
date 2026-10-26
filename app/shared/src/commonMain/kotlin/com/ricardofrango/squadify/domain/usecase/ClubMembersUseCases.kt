package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Paging

// Club settings → Members and Member details (designs 34, 35).

/** One page of the Members tab ([role] and [query] narrow it on the server), or just [userId] (Member details). */
class GetClubMembersOverviewUseCase(private val clubRepository: ClubRepository) {
    suspend operator fun invoke(
        clubId: String,
        role: ClubRole? = null,
        query: String = "",
        userId: String? = null,
        offset: Int = 0,
        limit: Int = Paging.PAGE_SIZE
    ): ClubMembersOverview = clubRepository.getClubMembersPage(clubId, role, query, userId, offset, limit)
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
