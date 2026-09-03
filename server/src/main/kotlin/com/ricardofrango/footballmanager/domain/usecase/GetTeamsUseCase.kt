package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Team

class GetTeamsUseCase(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Team> = transactionManager.runInTransaction {
        // 1. Check permissions first (Member of club, or a parent/player linked to someone in this club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

        val allTeams = teamRepository.getTeamsByClub(clubId)

        // 2. Club admins/coordinators (and platform admins, who never even reach here with a denial)
        //    see every team. Everyone else -- plain member, coach/staff, player, parent -- only sees the
        //    teams they actually have access to: a TeamRole assignment (TeamStaff) or a linked player on
        //    that team (TeamPlayers/UserPlayers). They should not see the whole club's team list just by
        //    being a club member.
        val user = userRepository.findById(userId)
        val userClubRole = userRepository.getUserRoleInClub(userId, clubId)
        val seesEverything = user?.isPlatformAdmin == true ||
            userClubRole == ClubRole.CLUB_ADMIN ||
            userClubRole == ClubRole.COORDINATOR

        if (seesEverything) {
            allTeams
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

            allTeams.filter { team ->
                teamRepository.getUserRoleInTeam(userId, team.id) != null || team.id in linkedTeamIds
            }
        }
    }
}
