package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.InviteRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.Club
import com.ricardofrango.footballmanager.model.ClubStatus
import com.ricardofrango.footballmanager.model.Invite
import com.ricardofrango.footballmanager.model.InviteType
import com.ricardofrango.footballmanager.model.ProvisionClubRequest
import com.ricardofrango.footballmanager.model.Role
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ProvisionClubUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val checkUserIsAdminUseCase: CheckUserIsAdminUseCase
) {
    suspend operator fun invoke(userId: String, request: ProvisionClubRequest): Club {
        // 1. Check if executor is Platform Admin
        checkUserIsAdminUseCase(userId).takeIf { it }
            ?: throw ForbiddenException("Only platform admins can perform this action")

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
            clubRepository.addMember(clubId, existingUser.id, Role.CLUB_ADMIN)
        } else {
            // Create invite for later registration
            val invite = Invite(
                id = Uuid.random().toString(),
                email = request.directorEmail,
                clubId = clubId,
                type = InviteType.CLUB_MEMBER,
                role = Role.CLUB_ADMIN,
                createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            )
            inviteRepository.createInvite(invite)
        }

        return createdClub
    }
}
