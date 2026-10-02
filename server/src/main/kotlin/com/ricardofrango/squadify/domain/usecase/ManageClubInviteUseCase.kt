package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.InviteType

/**
 * Resend or cancel a pending club-member invite (Club settings → Members, design 34). Admins and
 * coordinators only; the invite must belong to [clubId].
 */
class ManageClubInviteUseCase(
    private val inviteRepository: InviteRepository,
    private val clubRepository: ClubRepository,
    private val emailService: EmailService,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend fun resend(userId: String, clubId: String, inviteId: String) {
        var email = ""
        var clubName = "your club"
        transactionManager.runInTransaction {
            val invite = checkedInvite(userId, clubId, inviteId)
            email = invite.email
            clubRepository.getClubById(clubId)?.let { clubName = it.name }
        }
        // Sent after the transaction, as in AddClubMembersUseCase.
        emailService.sendClubInvite(to = email, clubName = clubName)
    }

    suspend fun cancel(userId: String, clubId: String, inviteId: String) = transactionManager.runInTransaction {
        checkedInvite(userId, clubId, inviteId)
        inviteRepository.deleteInvite(inviteId)
    }

    private suspend fun checkedInvite(userId: String, clubId: String, inviteId: String) =
        run {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.COORDINATOR,
                errorMessage = "Only admins or coordinators can manage invites"
            )
            inviteRepository.getById(inviteId)
                ?.takeIf { it.clubId == clubId && it.type == InviteType.CLUB_MEMBER }
                ?: throw NotFoundException("Invite not found")
        }
}
