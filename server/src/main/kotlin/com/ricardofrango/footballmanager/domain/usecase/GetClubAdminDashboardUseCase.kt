package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.model.ClubAdminDashboard
import com.ricardofrango.footballmanager.model.ClubRole

class GetClubAdminDashboardUseCase(
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val seasonRepository: SeasonRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): ClubAdminDashboard = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only club admins or coordinators can access this dashboard"
        )

        val totalTeams = teamRepository.countTeamsByClub(clubId)
        val totalPlayers = playerRepository.countPlayersByClub(clubId)
        val totalCoaches = teamRepository.countCoachesByClub(clubId)
        val upcomingMatches = matchRepository.getUpcomingMatchesByClub(clubId, limit = 10)
        val activeSeasons = seasonRepository.getActiveSeasonsByClub(clubId)

        ClubAdminDashboard(
            totalTeams = totalTeams,
            totalPlayers = totalPlayers,
            totalCoaches = totalCoaches,
            upcomingMatches = upcomingMatches,
            activeSeasons = activeSeasons
        )
    }
}
