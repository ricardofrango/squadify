package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.TeamSeasonMatch
import com.ricardofrango.squadify.model.TeamSeasonPlayer
import com.ricardofrango.squadify.model.TeamSeasonPlayerLine
import com.ricardofrango.squadify.model.TeamSeasonRecord

/**
 * Team Details' season record and team leaders (design 31): the team's finished matches with their
 * final scores (newest first), plus every player's stats line in them. The W/D/L record, form and
 * leader boards are derived client-side from these rows (see SeasonStats.kt). A team belongs to one
 * season, so this is that season's record. Same read gate as Team Details.
 *
 * Ratings follow the match report's read gate (coordinators/admins and this age group's staff) and
 * are removed here for anyone else. Only ratings of a completed report count.
 */
class GetTeamSeasonRecordUseCase(
    private val teamRepository: TeamRepository,
    private val seasonRepository: SeasonRepository,
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val matchPlayerStatsRepository: MatchPlayerStatsRepository,
    private val matchReportRepository: MatchReportRepository,
    private val opponentRepository: OpponentRepository,
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, teamId: String): TeamSeasonRecord = transactionManager.runInTransaction {
        val team = teamRepository.getTeamById(teamId) ?: throw NotFoundException("Team not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = team.clubId,
            minClubRole = ClubRole.MEMBER,
            minTeamRole = TeamRole.STAFF,
            teamId = teamId,
            allowPlayerOrParentAccess = true
        )

        val canSeeRatings = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = team.clubId,
                minClubRole = ClubRole.COORDINATOR,
                minTeamRole = TeamRole.STAFF,
                teamId = teamId
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        val season = seasonRepository.getSeasonById(team.seasonId) ?: throw NotFoundException("Season not found")
        val opponentsById = opponentRepository.getOpponentsByClub(team.clubId).associateBy { it.id }

        val finished = matchRepository.getMatchesByTeam(teamId)
            .filter { it.status == MatchStatus.FINISHED }
            .sortedByDescending { it.dateTime }
        val matches = finished.map { match ->
            val score = matchEventRepository.getScoreByMatch(match.id)
            TeamSeasonMatch(
                matchId = match.id,
                dateTime = match.dateTime,
                opponentName = opponentsById[match.opponentId]?.let { it.shortName ?: it.name } ?: "—",
                matchType = match.type,
                ourScore = score.ourScore,
                opponentScore = score.opponentScore
            )
        }

        val matchIds = finished.map { it.id }
        val completedReports = if (canSeeRatings) matchReportRepository.getCompletedMatchIds(matchIds) else emptySet()
        val lines = matchPlayerStatsRepository.getByMatches(matchIds).map { s ->
            TeamSeasonPlayerLine(
                matchId = s.matchId,
                playerId = s.playerId,
                minutesPlayed = s.minutesPlayed,
                goals = s.goals,
                assists = s.assists,
                rating = s.rating?.takeIf { s.matchId in completedReports }
            )
        }
        val players = playerRepository.getPlayersData(lines.map { it.playerId }.distinct())
            .map { TeamSeasonPlayer(it.id, "${it.firstName} ${it.lastName}") }

        TeamSeasonRecord(
            teamId = teamId,
            seasonName = season.name,
            matches = matches,
            playerLines = lines,
            players = players,
            canSeeRatings = canSeeRatings
        )
    }
}
