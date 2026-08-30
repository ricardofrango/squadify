package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.InviteRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.*
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AddClubMemberUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        request: AddClubMemberRequest
    ): Boolean {
        // 1. Check permissions (Admin/Coordinator required to invite members)
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can add members to the club"
        )

        val existingUser = userRepository.findByEmail(request.email)
        if (existingUser != null) {
            // Check if already a member
            val existingRole = userRepository.getUserRoleInClub(existingUser.id, clubId)
            if (existingRole != null) return true // Already a member, do nothing

            return clubRepository.addMember(clubId, existingUser.id, ClubRole.MEMBER)
        } else {
            val invite = Invite(
                id = Uuid.random().toString(),
                email = request.email,
                clubId = clubId,
                type = InviteType.CLUB_MEMBER,
                role = ClubRole.MEMBER, // Default role for new invites
                createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            )
            inviteRepository.createInvite(invite)
            return true
        }
    }
}
