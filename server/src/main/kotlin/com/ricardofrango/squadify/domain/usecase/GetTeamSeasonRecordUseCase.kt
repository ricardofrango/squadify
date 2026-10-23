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
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.Team
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
            minAgeGroupRole = AgeGroupRole.STAFF,
            teamId = teamId,
            allowPlayerOrParentAccess = true
        )

        val canSeeRatings = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = team.clubId,
                minClubRole = ClubRole.COORDINATOR,
                minAgeGroupRole = AgeGroupRole.STAFF,
                teamId = teamId
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        val season = seasonRepository.getSeasonById(team.seasonId) ?: throw NotFoundException("Season not found")
        buildTeamSeasonRecord(
            team = team,
            season = season,
            canSeeRatings = canSeeRatings,
            opponentsById = opponentRepository.getOpponentsByClub(team.clubId).associateBy { it.id },
            matchRepository = matchRepository,
            matchEventRepository = matchEventRepository,
            matchPlayerStatsRepository = matchPlayerStatsRepository,
            matchReportRepository = matchReportRepository,
            playerRepository = playerRepository
        )
    }
}

/**
 * One team's season record -- its finished matches with scores, newest first, and every called-up
 * player's line in them. Shared by Team Details (§4.6c) and Age Group Details (§4.3c). Ratings are
 * kept only when [canSeeRatings] and the match's report is completed.
 */
internal suspend fun buildTeamSeasonRecord(
    team: Team,
    season: Season,
    canSeeRatings: Boolean,
    opponentsById: Map<String, Opponent>,
    matchRepository: MatchRepository,
    matchEventRepository: MatchEventRepository,
    matchPlayerStatsRepository: MatchPlayerStatsRepository,
    matchReportRepository: MatchReportRepository,
    playerRepository: PlayerRepository
): TeamSeasonRecord {
    val finished = matchRepository.getMatchesByTeam(team.id)
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
    // Only the age group's current pool: a player taken out of it (or disabled) drops out of the
    // season's leaders and squad. The team's results and goals come from the matches, so they stay,
    // and each match still lists everyone who played in it (Match Details reads its own call-ups).
    val poolIds = playerRepository.getPlayersByAgeGroup(team.ageGroupId).map { it.id }.toSet()
    val lines = matchPlayerStatsRepository.getByMatches(matchIds).filter { it.playerId in poolIds }.map { s ->
        TeamSeasonPlayerLine(
            matchId = s.matchId,
            playerId = s.playerId,
            minutesPlayed = s.minutesPlayed,
            goals = s.goals,
            assists = s.assists,
            rating = s.rating?.takeIf { s.matchId in completedReports },
            started = s.started
        )
    }
    val players = playerRepository.getPlayersData(lines.map { it.playerId }.distinct()).map { it.toSeasonPlayer() }

    return TeamSeasonRecord(
        teamId = team.id,
        seasonId = season.id,
        seasonName = season.name,
        matches = matches,
        playerLines = lines,
        players = players,
        canSeeRatings = canSeeRatings,
        teamName = team.name
    )
}

internal fun Player.toSeasonPlayer() = TeamSeasonPlayer(id, "$firstName $lastName", shirtNumber, position, photoUrl)
