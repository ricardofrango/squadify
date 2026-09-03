package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.TeamDetails

class GetTeamDetailsUseCase(
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, teamId: String): TeamDetails = transactionManager.runInTransaction {
        val team = teamRepository.getTeamById(teamId)
            ?: throw ValidationException("Team not found")

        // 1. Check permissions first (Member of club)
        checkUserPermissionUseCase(
            userId = userId,
            clubId = team.clubId,
            minClubRole = ClubRole.MEMBER,
            minTeamRole = TeamRole.STAFF,
            teamId = teamId,
            allowPlayerOrParentAccess = true
        )

        val players = playerRepository.getPlayersByTeam(teamId)
        val staff = teamRepository.getTeamStaff(teamId)

        TeamDetails(
            team = team,
            players = players,
            staff = staff
        )
    }
}
