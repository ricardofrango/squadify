package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.TeamRole

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
                minTeamRole = TeamRole.STAFF,
                teamId = teamId,
                allowPlayerOrParentAccess = true
            )

            val team = teamRepository.getTeamById(teamId) ?: throw ValidationException("Team not found")
            playerRepository.getPlayersByAgeGroup(team.ageGroupId)
        } else {
            // No team filter: club admins/coordinators see every player in the club. Everyone else
            // only sees players in age groups they're attached to -- an AgeGroupStaff assignment or a
            // linked player in that age group's pool.
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
}
