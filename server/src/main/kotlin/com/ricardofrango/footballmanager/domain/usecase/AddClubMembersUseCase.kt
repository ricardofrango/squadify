package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.InviteRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.AddClubMembersRequest
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Invite
import com.ricardofrango.footballmanager.model.InviteType
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AddClubMembersUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        request: AddClubMembersRequest
    ): Boolean {
        // 1. Check permissions (Admin/Coordinator required to invite members)
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can add members to the club"
        )

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
                    membersToCreate.add(user.id to ClubRole.MEMBER)
                }
            } else {
                invitesToCreate.add(
                    Invite(
                        id = Uuid.random().toString(),
                        email = email,
                        clubId = clubId,
                        type = InviteType.CLUB_MEMBER,
                        role = ClubRole.MEMBER,
                        createdAt = now
                    )
                )
            }
        }

        if (membersToCreate.isNotEmpty()) {
            clubRepository.addMembers(clubId, membersToCreate)
        }

        if (invitesToCreate.isNotEmpty()) {
            inviteRepository.createInvites(invitesToCreate)
        }

        return true
    }
}
