package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerListFilter
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayersPage
import com.ricardofrango.squadify.model.SearchText
import com.ricardofrango.squadify.model.AgeGroupRole

class GetPlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
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
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.MEMBER,
                minAgeGroupRole = AgeGroupRole.STAFF,
                teamId = teamId,
                allowPlayerOrParentAccess = true
            )

            val team = teamRepository.getTeamById(teamId) ?: throw NotFoundException("Team not found")
            playerRepository.getPlayersByAgeGroup(team.ageGroupId)
        } else {
            // No team filter: club admins/coordinators see every player in the club. Everyone else
            // only sees players in age groups they're attached to -- an AgeGroupStaff assignment or a
            // linked player in that age group's pool.
            checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

            if (seesEverything(userId, clubId)) {
                playerRepository.getPlayersByClub(clubId)
            } else {
                val linkedPlayerIds = playerRepository.getPlayersByUser(userId)
                    .filter { it.clubId == clubId }
                    .map { it.id }
                    .toSet()
                val linkedAgeGroupIds = if (linkedPlayerIds.isEmpty()) {
                    emptySet()
                } else {
                    playerRepository.getAgeGroupPlayersByClub(clubId)
                        .filter { it.playerId in linkedPlayerIds }
                        .map { it.ageGroupId }
                        .toSet()
                }

                val visibleAgeGroupIds = ageGroupRepository.getAgeGroupsByClub(clubId)
                    .filter { ag ->
                        ageGroupRepository.getUserRoleInAgeGroup(userId, ag.id) != null || ag.id in linkedAgeGroupIds
                    }
                    .map { it.id }
                    .toSet()

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
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

        val visible = if (seesEverything(userId, clubId)) null
        else visibleAgeGroups(playerRepository, ageGroupRepository, userId, clubId).map { it.id }.toSet()
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

    // Club admins, coordinators and platform admins see every player of the club.
    private suspend fun seesEverything(userId: String, clubId: String): Boolean {
        val role = userRepository.getUserRoleInClub(userId, clubId)
        return role == ClubRole.CLUB_ADMIN || role == ClubRole.COORDINATOR || userRepository.findById(userId)?.isPlatformAdmin == true
    }
}
