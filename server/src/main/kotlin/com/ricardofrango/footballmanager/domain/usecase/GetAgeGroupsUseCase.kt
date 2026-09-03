package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.ClubRole

class GetAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<AgeGroup> = transactionManager.runInTransaction {
        // 1. Check permissions first (Member of club, or a parent/player linked to someone in this club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

        // 2. Club admins/coordinators see every age group. Everyone else -- plain member, coach/staff,
        //    player, parent -- only sees the age group(s) their own team(s) actually belong to. See
        //    "issues to fix.md" -- "A plain club member sees the whole club, not just 'the club.'"
        val user = userRepository.findById(userId)
        val userClubRole = userRepository.getUserRoleInClub(userId, clubId)
        val seesEverything = user?.isPlatformAdmin == true ||
            userClubRole == ClubRole.CLUB_ADMIN ||
            userClubRole == ClubRole.COORDINATOR

        if (seesEverything) {
            ageGroupRepository.getAgeGroupsByClub(clubId)
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

            val visibleAgeGroupIds = teamRepository.getTeamsByClub(clubId)
                .filter { team -> teamRepository.getUserRoleInTeam(userId, team.id) != null || team.id in linkedTeamIds }
                .map { it.ageGroupId }
                .toSet()

            visibleAgeGroupIds.mapNotNull { ageGroupRepository.getAgeGroupById(it) }
        }
    }
}
