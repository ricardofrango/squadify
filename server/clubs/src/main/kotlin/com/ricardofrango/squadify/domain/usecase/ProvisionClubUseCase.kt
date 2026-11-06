package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ProvisionClubUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val checkUserIsAdminUseCase: CheckUserIsAdminUseCase,
    private val transactionManager: TransactionManager,
    private val emailService: EmailService
) {
    suspend operator fun invoke(userId: String, request: ProvisionClubRequest, language: String? = null): Club {
        var existingDirector: User? = null

        val createdClub = transactionManager.runInTransaction {
            // 1. Check if executor is Platform Admin
            checkUserIsAdminUseCase(userId).takeIf { it }
                ?: throw com.ricardofrango.squadify.exception.ForbiddenException("Only platform admins can perform this action")

            // 2. Create the club first
            val clubId = Uuid.random().toString()
            val club = Club(
                id = clubId,
                name = request.clubName,
                status = ClubStatus.ACTIVE,
                sport = request.sport,
                createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            )
            val createdClub = clubRepository.createClub(club)

            // 3. Handle Director association
            val existingUser = userRepository.findByEmail(request.directorEmail)
            existingDirector = existingUser
            if (existingUser != null) {
                // Association directly
                clubRepository.addMember(clubId, existingUser.id, ClubRole.CLUB_ADMIN)
            } else {
                // Create invite for later registration
                val invite = Invite(
                    id = Uuid.random().toString(),
                    email = request.directorEmail,
                    clubId = clubId,
                    type = InviteType.CLUB_MEMBER,
                    role = ClubRole.CLUB_ADMIN,
                    createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                )
                inviteRepository.createInvite(invite)
            }

            createdClub
        }

        // Sent after the transaction commits -- a failed/slow email must never roll back club creation.
        val director = existingDirector
        if (director != null) {
            emailService.sendClubMembershipAdded(to = director.email, firstName = director.firstName, clubName = createdClub.name, language = language)
        } else {
            emailService.sendClubInvite(to = request.directorEmail, clubName = createdClub.name, language = language)
        }

        return createdClub
    }
}
