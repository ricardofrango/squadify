package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.ClubRepository
import com.ricardofrango.clubmanager.domain.repository.InviteRepository
import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.model.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ProvisionClubUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val checkUserIsAdminUseCase: CheckUserIsAdminUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: ProvisionClubRequest): Club = transactionManager.runInTransaction {
        // 1. Check if executor is Platform Admin
        checkUserIsAdminUseCase(userId).takeIf { it }
            ?: throw com.ricardofrango.clubmanager.exception.ForbiddenException("Only platform admins can perform this action")

        // 2. Create the club first
        val clubId = Uuid.random().toString()
        val club = Club(
            id = clubId,
            name = request.clubName,
            status = ClubStatus.ACTIVE,
            createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        )
        val createdClub = clubRepository.createClub(club)

        // 3. Handle Director association
        val existingUser = userRepository.findByEmail(request.directorEmail)
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
}
