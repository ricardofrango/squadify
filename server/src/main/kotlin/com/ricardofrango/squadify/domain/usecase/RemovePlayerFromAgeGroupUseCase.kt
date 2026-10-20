package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.MatchStatus

/**
 * Takes a player out of an age group's pool -- not out of the club. Behind the same permission as
 * adding players to it (`ASSIGN_PLAYERS_TO_AGE_GROUP`). Only the pool link goes: the player's matches
 * keep their call-ups, events and stats, so a match they played still shows them, while the age group's
 * and teams' season stats (built from the pool) no longer list them. They're also dropped from the
 * call-ups of the age group's matches that haven't started, which they can no longer play.
 */
class RemovePlayerFromAgeGroupUseCase(
    private val playerRepository: PlayerRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val teamRepository: TeamRepository,
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String, playerId: String) = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
        checkUserPermissionUseCase(
            userId = userId,
            clubId = ageGroup.clubId,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.ASSIGN_PLAYERS_TO_AGE_GROUP,
            ageGroupId = ageGroupId,
            errorMessage = "Only admins, coordinators, or this age group's coaches can remove players"
        )
        if (!playerRepository.removePlayerFromAgeGroup(ageGroupId, playerId)) {
            throw NotFoundException("Player is not in this age group")
        }
        dropFromUpcomingCallUps(playerId, ageGroup.clubId, setOf(ageGroupId), teamRepository, matchRepository)
    }
}

/**
 * Removes [playerId] from the call-ups of matches that haven't started (SCHEDULED) of the teams in
 * [ageGroupIds]. Started and played matches keep their call-ups: that's the match's history.
 */
internal suspend fun dropFromUpcomingCallUps(
    playerId: String,
    clubId: String,
    ageGroupIds: Set<String>,
    teamRepository: TeamRepository,
    matchRepository: MatchRepository
) {
    if (ageGroupIds.isEmpty()) return
    teamRepository.getTeamsByClub(clubId).filter { it.ageGroupId in ageGroupIds }.forEach { team ->
        matchRepository.getMatchesByTeam(team.id).filter { it.status == MatchStatus.SCHEDULED }.forEach { match ->
            val calledUp = matchRepository.getCallUpsForMatch(match.id).map { it.player.id }
            if (playerId in calledUp) matchRepository.manageCallUps(match.id, calledUp - playerId)
        }
    }
}
