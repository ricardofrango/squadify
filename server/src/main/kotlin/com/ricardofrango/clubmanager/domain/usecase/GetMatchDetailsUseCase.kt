package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.domain.repository.OpponentRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.SeasonRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ForbiddenException
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.MatchDetails
import com.ricardofrango.clubmanager.model.TeamRole

class GetMatchDetailsUseCase(
    private val matchRepository: MatchRepository,
    private val opponentRepository: OpponentRepository,
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): MatchDetails = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        checkUserPermissionUseCase(
            userId,
            match.clubId,
            minClubRole = ClubRole.MEMBER,
            minTeamRole = TeamRole.STAFF,
            teamId = match.teamId,
            allowPlayerOrParentAccess = true
        )

        val opponent = opponentRepository.getOpponentById(match.opponentId)
            ?: throw NotFoundException("Opponent not found")
        val team = teamRepository.getTeamById(match.teamId)
            ?: throw NotFoundException("Team not found")
        val ageGroup = ageGroupRepository.getAgeGroupById(team.ageGroupId)
            ?: throw NotFoundException("Age group not found")
        val season = seasonRepository.getSeasonById(team.seasonId)
            ?: throw NotFoundException("Season not found")

        val callUps = matchRepository.getCallUpsForMatch(matchId)
        val calledUpIds = callUps.map { it.player.id }.toSet()
        // The age group's pool minus whoever is already in the call-up -- the "Available" list.
        val poolPlayers = playerRepository.getPlayersByAgeGroup(team.ageGroupId)
            .filter { it.id !in calledUpIds }

        val canManage = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = match.clubId,
                minClubRole = ClubRole.COORDINATOR,
                minTeamRole = TeamRole.COACH,
                teamId = match.teamId
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        MatchDetails(
            match = match,
            opponent = opponent,
            team = team,
            ageGroup = ageGroup,
            season = season,
            callUps = callUps,
            poolPlayers = poolPlayers,
            canManage = canManage
        )
    }
}
