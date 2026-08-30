package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.InviteRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.AddClubMemberRequest
import com.ricardofrango.footballmanager.model.Invite
import com.ricardofrango.footballmanager.model.InviteType
import com.ricardofrango.footballmanager.model.Role
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
        // 1. Check permissions (Admin/Coordinator required to add members)
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minRole = Role.COORDINATOR,
            errorMessage = "Only admins or coordinators can add members to the club"
        )

        val existingUser = userRepository.findByEmail(request.email)
        if (existingUser != null) {
            return clubRepository.addMember(clubId, existingUser.id, request.role)
        } else {
            val invite = Invite(
                id = Uuid.random().toString(),
                email = request.email,
                clubId = clubId,
                type = InviteType.CLUB_MEMBER,
                role = request.role,
                createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            )
            inviteRepository.createInvite(invite)
            return true
        }
    }
}
