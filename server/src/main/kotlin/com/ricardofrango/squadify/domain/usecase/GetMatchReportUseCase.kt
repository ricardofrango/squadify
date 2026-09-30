package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.MatchPlayerStatsWithPlayer
import com.ricardofrango.squadify.model.MatchReportDetails
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.TeamRole

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
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): MatchReportDetails = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = match.clubId,
            minClubRole = ClubRole.COORDINATOR,
            minTeamRole = TeamRole.STAFF,
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

        MatchReportDetails(
            match = match,
            report = matchReportRepository.getByMatch(matchId),
            playerStats = playerStats,
            canEdit = canEdit
        )
    }
}
