package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UpdateTeamRequest

class UpdateTeamUseCase(
    private val teamRepository: TeamRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
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

        val updatedTeam = existingTeam.copy(
            name = request.name ?: existingTeam.name,
            code = request.code ?: existingTeam.code,
            active = request.active ?: existingTeam.active
        )

        teamRepository.updateTeam(updatedTeam)
    }
}
