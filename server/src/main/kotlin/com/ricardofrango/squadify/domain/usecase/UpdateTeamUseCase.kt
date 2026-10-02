package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UpdateTeamRequest

class UpdateTeamUseCase(
    private val teamRepository: TeamRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(
        userId: String,
        teamId: String,
        request: UpdateTeamRequest
    ): Boolean = transactionManager.runInTransaction {
        val existingTeam = teamRepository.getTeamById(teamId)
            ?: throw NotFoundException("Team not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = existingTeam.clubId,
            minClubRole = ClubRole.COORDINATOR,
            minTeamRole = TeamRole.COACH,
            teamId = teamId,
            errorMessage = "Only admins, coordinators, or this team's coach can update it"
        )

        if (request.name?.isBlank() == true) {
            throw ValidationException("Team name cannot be empty")
        }
        val teamSize = request.teamSize
        if (teamSize != null) {
            val club = clubRepository.getClubById(existingTeam.clubId) ?: throw NotFoundException("Club not found")
            if (teamSize !in club.sport.teamSizes) {
                throw ValidationException("Team size $teamSize is not valid for ${club.sport}")
            }
        }

        val updatedTeam = existingTeam.copy(
            name = request.name?.trim() ?: existingTeam.name,
            code = request.code ?: existingTeam.code,
            active = request.active ?: existingTeam.active,
            teamSize = teamSize ?: existingTeam.teamSize
        )

        teamRepository.updateTeam(updatedTeam)
    }
}
