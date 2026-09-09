package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.TeamRole
import com.ricardofrango.clubmanager.model.UpdateTeamRequest

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
