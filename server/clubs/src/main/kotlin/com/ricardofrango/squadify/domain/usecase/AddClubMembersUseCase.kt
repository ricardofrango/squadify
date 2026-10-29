package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.model.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AddClubMembersUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    private val transactionManager: TransactionManager,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val emailService: EmailService,
    private val pushNotificationService: PushNotificationService
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        request: AddClubMembersRequest
    ): Boolean {
        val membersAdded = mutableListOf<User>()
        val emailsInvited = mutableListOf<String>()
        var clubName = "your club"
        var pushTokensByUser: Map<String, List<String>> = emptyMap()

        transactionManager.runInTransaction {
            // 1. Check permissions (Admin/Coordinator required to invite members)
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.COORDINATOR,
                errorMessage = "Only admins or coordinators can add members to the club"
            )

            // clubRepository has no ambient transaction of its own outside this block -- fetched here,
            // not after the transaction closes, even though it's only used for the emails sent below.
            clubRepository.getClubById(clubId)?.let { clubName = it.name }

            // Admin can only be granted once someone is in (design 34).
            if (request.role == ClubRole.CLUB_ADMIN) throw ValidationException("Invite as Member or Coordinator; make admins once they've joined")

            val uniqueEmails = request.emails.distinct()
            val existingUsers = userRepository.findByEmails(uniqueEmails).associateBy { it.email }
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

            val userIdsToCheck = existingUsers.values.map { it.id }
            val userRolesMap = userRepository.getUserRolesInClub(userIdsToCheck, clubId)

            val invitesToCreate = mutableListOf<Invite>()
            val membersToCreate = mutableListOf<Pair<String, ClubRole>>()

            uniqueEmails.forEach { email ->
                val user = existingUsers[email]
                if (user != null) {
                    // Check if already a member
                    if (userRolesMap[user.id] == null) {
                        membersToCreate.add(user.id to request.role)
                        membersAdded.add(user)
                    }
                } else {
                    invitesToCreate.add(
                        Invite(
                            id = Uuid.random().toString(),
                            email = email,
                            clubId = clubId,
                            type = InviteType.CLUB_MEMBER,
                            role = request.role,
                            createdAt = now
                        )
                    )
                    emailsInvited.add(email)
                }
            }

            if (membersToCreate.isNotEmpty()) {
                clubRepository.addMembers(clubId, membersToCreate)
            }

            if (invitesToCreate.isNotEmpty()) {
                inviteRepository.createInvites(invitesToCreate)
            }

            // fcmTokenRepository has no ambient transaction of its own outside this block either --
            // fetched here for the same reason clubName is, even though it's only used for the push
            // notifications sent below.
            if (membersAdded.isNotEmpty()) {
                pushTokensByUser = fcmTokenRepository.getTokensForUsers(membersAdded.map { it.id })
            }

            true
        }

        // Sent after the transaction commits -- a failed/slow email or push must never roll back
        // membership.
        if (membersAdded.isNotEmpty() || emailsInvited.isNotEmpty()) {
            membersAdded.forEach {
                emailService.sendClubMembershipAdded(to = it.email, firstName = it.firstName, clubName = clubName)
                pushTokensByUser[it.id]?.let { tokens ->
                    pushNotificationService.sendClubMembershipAdded(tokens = tokens, link = PushLink.club(clubId), clubName = clubName)
                }
            }
            emailsInvited.forEach {
                emailService.sendClubInvite(to = it, clubName = clubName)
            }
        }

        return true
    }
}
