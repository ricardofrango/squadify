package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Team

class GetTeamsUseCase(
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Team> = transactionManager.runInTransaction {
        // 1. Check permissions first (Member of club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER)

        val allTeams = teamRepository.getTeamsByClub(clubId)

        // 2. Club admins/coordinators (and platform admins, who never even reach here with a denial)
        //    see every team. A plain member only sees the teams they actually have a TeamRole on --
        //    they should not see the whole club's team list just by being a club member.
        val user = userRepository.findById(userId)
        val userClubRole = userRepository.getUserRoleInClub(userId, clubId)
        val seesEverything = user?.isPlatformAdmin == true ||
            userClubRole == ClubRole.CLUB_ADMIN ||
            userClubRole == ClubRole.COORDINATOR

        if (seesEverything) {
            allTeams
        } else {
            allTeams.filter { team -> teamRepository.getUserRoleInTeam(userId, team.id) != null }
        }
    }
}
