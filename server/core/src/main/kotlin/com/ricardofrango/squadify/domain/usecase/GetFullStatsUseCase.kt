package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.scope
import com.ricardofrango.squadify.domain.access.allowed
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.FullStatsData
import com.ricardofrango.squadify.model.FullStatsLine
import com.ricardofrango.squadify.model.FullStatsMatch
import com.ricardofrango.squadify.model.FullStatsTeam
import com.ricardofrango.squadify.model.FullStatsTraining
import com.ricardofrango.squadify.model.MatchStatus
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The Full stats screen's data (REQUIREMENTS: "Full stats screen"): the season's finished matches with
 * scores, every player's line in them, and the age group's ended training sessions with their ratings.
 * The table itself is derived client-side (`FullStats.kt`).
 *
 * Entering from a team or from its age group gives the same data, the whole age group's (its pool and
 * every team's matches), so the screen always offers the same team filter; from a team, the app just
 * preselects it, and the table then lists only who played for that team (a start or a minute).
 *
 * Same read gate as Team / Age Group Details. Match and training ratings follow the ratings gate
 * (coordinators/admins and the age group's staff): stripped, and no trainings sent, for anyone else.
 * As in the season record, only the age group's current pool counts, and only ratings of a completed report.
 */
class GetFullStatsUseCase(
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val matchPlayerStatsRepository: MatchPlayerStatsRepository,
    private val matchReportRepository: MatchReportRepository,
    private val playerRepository: PlayerRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    private val trainingRatingRepository: TrainingRatingRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    suspend fun team(userId: String, teamId: String): FullStatsData = transactionManager.runInTransaction {
        val team = teamRepository.getTeamById(teamId) ?: throw NotFoundException("Team not found")
        accessPolicy.requireViewGames(userId, team.scope)
        accessPolicy.requireStatsInPlan(team.clubId)
        val ageGroup = ageGroupRepository.getAgeGroupById(team.ageGroupId) ?: throw NotFoundException("Age group not found")
        build(userId, ageGroup)
    }

    suspend fun ageGroup(userId: String, ageGroupId: String): FullStatsData = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
        accessPolicy.requireViewGames(userId, GameScope(ageGroup.clubId, ageGroupId = ageGroupId))
        accessPolicy.requireStatsInPlan(ageGroup.clubId)
        build(userId, ageGroup)
    }

    private suspend fun canSeeRatings(userId: String, ageGroup: AgeGroup) =
        allowed { accessPolicy.requireSeeRatings(userId, GameScope(ageGroup.clubId, ageGroupId = ageGroup.id)) }

    private suspend fun build(userId: String, ageGroup: AgeGroup): FullStatsData {
        val canSeeRatings = canSeeRatings(userId, ageGroup)
        val teams = teamRepository.getTeamsByClub(ageGroup.clubId).filter { it.ageGroupId == ageGroup.id }.sortedBy { it.name }
        val season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: throw NotFoundException("Season not found")
        val finished = teams.flatMap { team -> matchRepository.getMatchesByTeam(team.id).filter { it.status == MatchStatus.FINISHED } }
        val matches = finished.map { match ->
            val score = matchEventRepository.getScoreByMatch(match.id)
            FullStatsMatch(match.id, match.teamId, match.dateTime, match.type, score.ourScore, score.opponentScore)
        }
        val matchIds = finished.map { it.id }
        val completedReports = if (canSeeRatings) matchReportRepository.getCompletedMatchIds(matchIds) else emptySet()
        val pool = playerRepository.getPlayersByAgeGroup(ageGroup.id)
        val poolIds = pool.map { it.id }.toSet()
        val lines = matchPlayerStatsRepository.getByMatches(matchIds).filter { it.playerId in poolIds }.map { s ->
            FullStatsLine(
                matchId = s.matchId,
                playerId = s.playerId,
                started = s.started,
                minutesPlayed = s.minutesPlayed,
                goals = s.goals,
                assists = s.assists,
                yellowCards = s.yellowCards,
                redCards = s.redCards,
                rating = s.rating?.takeIf { s.matchId in completedReports }
            )
        }
        val trainings = if (!canSeeRatings) emptyList() else {
            val sessions = trainingSessionRepository.getByAgeGroup(ageGroup.id).map { it.session }.filter { it.ratingsOpen(now()) }
            val ratings = trainingRatingRepository.getForSessions(sessions.map { it.id })
            sessions.map { FullStatsTraining(it.id, it.date, ratings[it.id].orEmpty().filter { r -> r.playerId in poolIds }) }
        }

        return FullStatsData(
            title = ageGroup.name,
            ageGroupId = ageGroup.id,
            seasonId = season.id,
            seasonName = season.name,
            seasonStart = season.startDate,
            seasonEnd = season.endDate,
            teams = teams.map { FullStatsTeam(it.id, it.name) },
            matches = matches,
            lines = lines,
            trainings = trainings,
            players = pool.map { it.toSeasonPlayer() },
            canSeeRatings = canSeeRatings
        )
    }

    private fun now(): LocalDateTime = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
