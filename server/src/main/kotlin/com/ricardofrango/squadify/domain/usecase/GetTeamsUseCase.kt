package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Team

class GetTeamsUseCase(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val playerRepository: PlayerRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Team> = transactionManager.runInTransaction {
        // 1. Check permissions first (Member of club, or a parent/player linked to someone in this club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

        val allTeams = teamRepository.getTeamsByClub(clubId)

        // 2. Club admins/coordinators (and platform admins) see every team. Everyone else -- plain
        //    member, coach/staff, player, parent -- only sees teams whose age group they're attached
        //    to: an AgeGroupStaff assignment (coach/staff) or a linked player in that age group's pool.
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
            val linkedAgeGroupIds = if (linkedPlayerIds.isEmpty()) {
                emptySet()
            } else {
                playerRepository.getAgeGroupPlayersByClub(clubId)
                    .filter { it.playerId in linkedPlayerIds }
                    .map { it.ageGroupId }
                    .toSet()
            }

            allTeams.filter { team ->
                ageGroupRepository.getUserRoleInAgeGroup(userId, team.ageGroupId) != null ||
                    team.ageGroupId in linkedAgeGroupIds
            }
        }
    }
}
