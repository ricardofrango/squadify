package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Season

class GetSeasonsUseCase(
    private val seasonRepository: SeasonRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Season> = transactionManager.runInTransaction {
        // 1. Check permissions first (Member of club, or a parent/player linked to someone in this club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

        // 2. Club admins/coordinators see every season. Everyone else -- plain member, coach/staff,
        //    player, parent -- only sees the season(s) their own team(s) actually belong to. See
        //    "issues to fix.md" -- "A plain club member sees the whole club, not just 'the club.'"
        val user = userRepository.findById(userId)
        val userClubRole = userRepository.getUserRoleInClub(userId, clubId)
        val seesEverything = user?.isPlatformAdmin == true ||
            userClubRole == ClubRole.CLUB_ADMIN ||
            userClubRole == ClubRole.COORDINATOR

        if (seesEverything) {
            seasonRepository.getSeasonsByClub(clubId)
        } else {
            val linkedPlayerIds = playerRepository.getPlayersByUser(userId)
                .filter { it.clubId == clubId }
                .map { it.id }
                .toSet()
            val linkedTeamIds = if (linkedPlayerIds.isEmpty()) {
                emptySet()
            } else {
                playerRepository.getTeamPlayersByClub(clubId)
                    .filter { it.playerId in linkedPlayerIds }
                    .map { it.teamId }
                    .toSet()
            }

            val visibleSeasonIds = teamRepository.getTeamsByClub(clubId)
                .filter { team -> teamRepository.getUserRoleInTeam(userId, team.id) != null || team.id in linkedTeamIds }
                .map { it.seasonId }
                .toSet()

            visibleSeasonIds.mapNotNull { seasonRepository.getSeasonById(it) }
        }
    }
}
