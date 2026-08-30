package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.InviteRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.AddStaffToTeamRequest
import com.ricardofrango.footballmanager.model.Invite
import com.ricardofrango.footballmanager.model.InviteType
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.TeamStaff
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AddStaffToTeamUseCase(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val inviteRepository: InviteRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        teamId: String,
        request: AddStaffToTeamRequest
    ): Boolean {
        // 1. Verify team exists first to get context
        val team = teamRepository.getTeamById(teamId)
            ?: throw ValidationException("Team not found")

        // 2. Check permissions
        checkUserPermissionUseCase(
            userId = userId,
            clubId = team.clubId,
            minRole = Role.COORDINATOR,
            teamId = teamId,
            errorMessage = "Only admins or coordinators can add staff to teams"
        )

        val existingUser = userRepository.findByEmail(request.userEmail)
        if (existingUser != null) {
            val teamStaff = TeamStaff(
                id = Uuid.random().toString(),
                teamId = teamId,
                userId = existingUser.id,
                role = request.role
            )
            return teamRepository.addStaffToTeam(teamStaff)
        } else {
            val invite = Invite(
                id = Uuid.random().toString(),
                email = request.userEmail,
                clubId = team.clubId,
                type = InviteType.TEAM_MEMBER,
                role = Role.COACH, // Default club role for team staff
                teamId = teamId,
                teamRole = request.role,
                createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            )
            inviteRepository.createInvite(invite)
            return true
        }
    }
}
