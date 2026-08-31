package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchEventRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.model.ParentDashboard
import com.ricardofrango.footballmanager.model.ParentDashboardChild

class GetParentDashboardUseCase(
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository
) {
    suspend operator fun invoke(userId: String): ParentDashboard {
        // A Parent/User can always access their own dashboard (limited to their linked children)
        val children = playerRepository.getPlayersByUser(userId)

        val childSummaries = children.map { player ->
            val upcomingMatches = matchRepository.getUpcomingMatchesByPlayer(player.id, limit = 5)
            val recentEvents = matchEventRepository.getRecentEventsByPlayer(player.id, limit = 5)

            ParentDashboardChild(
                playerId = player.id,
                firstName = player.firstName,
                lastName = player.lastName,
                upcomingMatches = upcomingMatches,
                recentEvents = recentEvents
            )
        }

        return ParentDashboard(children = childSummaries)
    }
}
