package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.Match

class GetMatchesUseCase(
    private val matchRepository: MatchRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val playerRepository: PlayerRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String, teamId: String? = null): List<Match> = transactionManager.runInTransaction {
        if (teamId != null) {
            // Scoped to one team: a club admin/coordinator, that team's coach/staff, or a player/parent
            // linked to that team can see its match calendar.
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.MEMBER,
                minTeamRole = TeamRole.STAFF,
                teamId = teamId,
                allowPlayerOrParentAccess = true
            )

            matchRepository.getMatchesByTeam(teamId)
        } else {
            // No team filter: club admins/coordinators see every match in the club. Everyone else --
            // plain member, coach/staff, player, parent -- only sees matches for teams they actually have
            // access to (a TeamRole assignment or a linked player via TeamPlayers/UserPlayers). See
            // "issues to fix.md" -- "A plain club member sees the whole club, not just 'the club.'"
            checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

            val user = userRepository.findById(userId)
            val userClubRole = userRepository.getUserRoleInClub(userId, clubId)
            val seesEverything = user?.isPlatformAdmin == true ||
                userClubRole == ClubRole.CLUB_ADMIN ||
                userClubRole == ClubRole.COORDINATOR

            if (seesEverything) {
                matchRepository.getMatchesByClub(clubId)
            } else {
                val visibleAgeGroupIds = visibleAgeGroups(playerRepository, ageGroupRepository, userId, clubId)
                    .map { it.id }
                    .toSet()
                val visibleTeamIds = teamRepository.getTeamsByClub(clubId)
                    .filter { it.ageGroupId in visibleAgeGroupIds }
                    .map { it.id }

                visibleTeamIds.flatMap { matchRepository.getMatchesByTeam(it) }.distinctBy { it.id }
            }
        }
    }
}
