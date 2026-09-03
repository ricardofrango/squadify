package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.TeamRole

class GetPlayersUseCase(
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        teamId: String? = null
    ): List<Player> = transactionManager.runInTransaction {
        if (teamId != null) {
            // Scoped to one team: a club admin/coordinator, that team's coach/staff, or a player/parent
            // linked to that team can see its roster.
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.MEMBER,
                minTeamRole = TeamRole.STAFF,
                teamId = teamId,
                allowPlayerOrParentAccess = true
            )

            playerRepository.getPlayersByTeam(teamId)
        } else {
            // No team filter: club admins/coordinators see every player in the club. Everyone else --
            // plain member, coach/staff, player, parent -- only sees players on teams they actually have
            // access to (a TeamRole assignment or a linked player via TeamPlayers/UserPlayers). See
            // "issues to fix.md" -- "A plain club member sees the whole club, not just 'the club.'"
            checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

            val user = userRepository.findById(userId)
            val userClubRole = userRepository.getUserRoleInClub(userId, clubId)
            val seesEverything = user?.isPlatformAdmin == true ||
                userClubRole == ClubRole.CLUB_ADMIN ||
                userClubRole == ClubRole.COORDINATOR

            if (seesEverything) {
                playerRepository.getPlayersByClub(clubId)
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

                val visibleTeamIds = teamRepository.getTeamsByClub(clubId)
                    .filter { team -> teamRepository.getUserRoleInTeam(userId, team.id) != null || team.id in linkedTeamIds }
                    .map { it.id }

                visibleTeamIds.flatMap { playerRepository.getPlayersByTeam(it) }.distinctBy { it.id }
            }
        }
    }
}
