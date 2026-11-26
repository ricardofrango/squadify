package com.sportssoftw.squadcall.domain.usecase

import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import com.sportssoftw.squadcall.model.currentSeason
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.model.ClubAdminDashboard
import com.sportssoftw.squadcall.model.ClubRole

class GetClubAdminDashboardUseCase(
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val seasonRepository: SeasonRepository,
    private val ageGroupRepository: AgeGroupRepository,
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
        val totalCoaches = ageGroupRepository.countCoachesByClub(clubId)
        val upcomingMatches = matchRepository.getUpcomingMatchesByClub(clubId, limit = 10)
        // The current season (Season.currentSeason), by today's date.
        val activeSeasons = listOfNotNull(seasonRepository.getSeasonsByClub(clubId).currentSeason(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date))

        ClubAdminDashboard(
            totalTeams = totalTeams,
            totalPlayers = totalPlayers,
            totalCoaches = totalCoaches,
            upcomingMatches = upcomingMatches,
            activeSeasons = activeSeasons
        )
    }
}
