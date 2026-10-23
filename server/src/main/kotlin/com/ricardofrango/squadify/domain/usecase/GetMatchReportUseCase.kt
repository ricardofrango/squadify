package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.MatchPlayerStatsWithPlayer
import com.ricardofrango.squadify.model.MatchReportDetails
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.AgeGroupRole

/**
 * The match report screen's data: the report (null until first saved), each called-up player's
 * stats line with their rating, and whether the caller can edit.
 *
 * Readable by club coordinators/admins and the match's age-group staff only. Unlike the rest of
 * the match, players and parents can't read it, since it holds ratings and the coach's notes.
 */
class GetMatchReportUseCase(
    private val matchRepository: MatchRepository,
    private val matchReportRepository: MatchReportRepository,
    private val matchPlayerStatsRepository: MatchPlayerStatsRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): MatchReportDetails = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minClubRole = ClubRole.COORDINATOR,
            minAgeGroupRole = AgeGroupRole.STAFF,
            teamId = match.teamId,
            errorMessage = "Only admins, coordinators, or this age group's staff can read the match report"
        )

        if (match.status != MatchStatus.FINISHED) {
            throw ValidationException("A match report only exists once the match has finished")
        }

        val playersById = matchRepository.getCallUpsForMatch(matchId).associate { it.player.id to it.player }
        val playerStats = matchPlayerStatsRepository.getByMatch(matchId).mapNotNull { stats ->
            playersById[stats.playerId]?.let { MatchPlayerStatsWithPlayer(stats, it) }
        }

        // Non-throwing probe of the same gate SaveMatchReportUseCase enforces.
        val canEdit = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = match.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.MANAGE_MATCH,
                teamId = match.teamId
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        val report = matchReportRepository.getByMatch(matchId)
        val team = teamRepository.getTeamById(match.teamId) ?: throw NotFoundException("Team not found")
        val opponent = opponentRepository.getOpponentById(match.opponentId) ?: throw NotFoundException("Opponent not found")
        MatchReportDetails(
            match = match,
            teamName = team.name,
            opponentName = opponent.shortName ?: opponent.name,
            report = report,
            updatedByName = report?.let { userRepository.findById(it.updatedByUserId) }?.let { "${it.firstName} ${it.lastName}" },
            playerStats = playerStats,
            canEdit = canEdit
        )
    }
}
