package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.GameScope
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.PlayerListFilter
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.Player
import com.sportssoftw.squadcall.model.PlayersPage
import com.sportssoftw.squadcall.model.SearchText

class GetPlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        teamId: String? = null
    ): List<Player> = transactionManager.runInTransaction {
        if (teamId != null) {
            // Scoped to one team: a club admin/coordinator, that team's age-group coach/staff, or a
            // player/parent linked to a player in that age group can see the pool.
            accessPolicy.requireViewGames(userId, GameScope(clubId, teamId = teamId))

            val team = teamRepository.getTeamById(teamId) ?: throw NotFoundException("Team not found")
            playerRepository.getPlayersByAgeGroup(team.ageGroupId)
        } else {
            // No team filter: club admins/coordinators see every player in the club. Everyone else
            // only sees players in age groups they're attached to -- an AgeGroupStaff assignment or a
            // linked player in that age group's pool.
            accessPolicy.requireViewClub(userId, clubId)

            val visibleAgeGroupIds = accessPolicy.visibleAgeGroupIds(userId, clubId)
            if (visibleAgeGroupIds == null) {
                playerRepository.getPlayersByClub(clubId)
            } else {
                visibleAgeGroupIds.flatMap { playerRepository.getPlayersByAgeGroup(it) }.distinctBy { it.id }
            }
        }
    }

    /**
     * One page of the club's players for the Players screen (`GET /players` with `limit`): the season,
     * age-group and name filters run here rather than on the device, active players first, then by
     * name. Below coordinator, only the age groups the viewer is attached to, as in [invoke].
     */
    suspend fun page(
        userId: String,
        clubId: String,
        seasonId: String?,
        ageGroupId: String?,
        search: String?,
        limit: Int,
        offset: Int
    ): PlayersPage = transactionManager.runInTransaction {
        accessPolicy.requireViewClub(userId, clubId)

        val visible = accessPolicy.visibleAgeGroupIds(userId, clubId)
        val filter = PlayerListFilter(
            visibleAgeGroupIds = visible,
            seasonAgeGroupIds = seasonId?.let { season ->
                ageGroupRepository.getAgeGroupsByClub(clubId).filter { it.seasonId == season }.map { it.id }.toSet()
            },
            ageGroupId = ageGroupId,
            search = search?.trim()?.let(SearchText::key)?.takeIf { it.isNotEmpty() }
        )
        // One extra row says whether another page follows.
        val rows = playerRepository.getPlayersPage(clubId, filter, limit + 1, offset)
        PlayersPage(
            players = rows.take(limit),
            hasMore = rows.size > limit,
            anyPlayers = rows.isNotEmpty() || playerRepository.hasPlayers(clubId, PlayerListFilter(visibleAgeGroupIds = visible))
        )
    }
}
