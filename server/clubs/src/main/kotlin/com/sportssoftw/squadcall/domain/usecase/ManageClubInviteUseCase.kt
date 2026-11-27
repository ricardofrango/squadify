package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.InviteRepository
import com.sportssoftw.squadcall.domain.service.EmailService
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.InviteType

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
    suspend fun resend(userId: String, clubId: String, inviteId: String, language: String? = null) {
        var email = ""
        var clubName = "your club"
        transactionManager.runInTransaction {
            val invite = checkedInvite(userId, clubId, inviteId)
            email = invite.email
            clubRepository.getClubById(clubId)?.let { clubName = it.name }
        }
        // Sent after the transaction, as in AddClubMembersUseCase.
        emailService.sendClubInvite(to = email, clubName = clubName, language = language)
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
