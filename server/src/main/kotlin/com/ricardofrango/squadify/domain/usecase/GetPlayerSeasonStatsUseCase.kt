package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
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
import com.ricardofrango.squadify.model.PlayerSeason
import com.ricardofrango.squadify.model.PlayerSeasonMatch
import com.ricardofrango.squadify.model.PlayerSeasonStats
import com.ricardofrango.squadify.model.AgeGroupRole

/**
 * Player Details' season stats (design 30): every season the player was registered in, newest first,
 * each with his per-match stats lines (newest first). Totals and the match-type filter are derived
 * client-side from these rows (see SeasonStats.kt). Readable by any club member, like the profile.
 *
 * Ratings follow the match report's own read gate (coordinators/admins and the match's age-group
 * staff): for anyone else they're removed here, not just hidden by the client. Only ratings of a
 * completed report count -- a draft's ratings aren't final.
 */
class GetPlayerSeasonStatsUseCase(
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val matchPlayerStatsRepository: MatchPlayerStatsRepository,
    private val matchReportRepository: MatchReportRepository,
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, playerId: String): PlayerSeasonStats = transactionManager.runInTransaction {
        val player = playerRepository.getPlayerById(playerId) ?: throw NotFoundException("Player not found")
        checkUserPermissionUseCase(userId, player.clubId, minClubRole = ClubRole.MEMBER)

        val matchesById = matchRepository.getMatchesByClub(player.clubId).associateBy { it.id }
        val teamsById = teamRepository.getTeamsByClub(player.clubId).associateBy { it.id }
        val seasonsById = seasonRepository.getSeasonsByClub(player.clubId).associateBy { it.id }
        val opponentsById = opponentRepository.getOpponentsByClub(player.clubId).associateBy { it.id }

        val statsLines = matchPlayerStatsRepository.getByPlayer(playerId).filter { it.matchId in matchesById }
        val completedReports = matchReportRepository.getCompletedMatchIds(statsLines.map { it.matchId })

        // The report's read gate, probed once per team the player has lines in.
        suspend fun canSeeRatings(teamId: String?, ageGroupId: String?): Boolean = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = player.clubId,
                minClubRole = ClubRole.COORDINATOR,
                minAgeGroupRole = AgeGroupRole.STAFF,
                teamId = teamId,
                ageGroupId = ageGroupId
            )
            true
        } catch (e: ForbiddenException) {
            false
        }
        val canRateByTeam = statsLines.mapNotNull { matchesById[it.matchId]?.teamId }.distinct()
            .associateWith { canSeeRatings(teamId = it, ageGroupId = null) }
        val registeredAgeGroupIds = playerRepository.getAgeGroupIdsByPlayer(playerId)

        // Each line, tagged with the season of the team that played the match.
        val rowsBySeason = statsLines.mapNotNull { line ->
            val match = matchesById.getValue(line.matchId)
            val team = teamsById[match.teamId] ?: return@mapNotNull null
            val opponent = opponentsById[match.opponentId]
            val rating = line.rating?.takeIf { line.matchId in completedReports && canRateByTeam[team.id] == true }
            team.seasonId to (team.name to PlayerSeasonMatch(
                matchId = match.id,
                dateTime = match.dateTime,
                opponentName = opponent?.let { it.shortName ?: it.name } ?: "—",
                matchType = match.type,
                started = line.started,
                minutesPlayed = line.minutesPlayed,
                goals = line.goals,
                assists = line.assists,
                ownGoals = line.ownGoals,
                yellowCards = line.yellowCards,
                redCards = line.redCards,
                rating = rating,
                teamName = team.name
            ))
        }.groupBy({ it.first }, { it.second })

        // Finished matches he was called up for before stats existed (no full-time minute recorded):
        // they can't be totalled, but the season sheet says he played them (design 30c).
        val withStats = statsLines.map { it.matchId }.toSet()
        val preStatsBySeason = matchRepository.getCallUpMatchIdsByPlayer(playerId)
            .mapNotNull { matchesById[it] }
            .filter { it.status == MatchStatus.FINISHED && it.fullTimeMinute == null && it.id !in withStats }
            .mapNotNull { match -> teamsById[match.teamId]?.let { it.seasonId to it.name } }
            .groupBy({ it.first }, { it.second })

        val registeredSeasonIds = registeredAgeGroupIds
            .mapNotNull { ageGroupRepository.getAgeGroupById(it)?.seasonId }

        val seasons = (registeredSeasonIds + rowsBySeason.keys + preStatsBySeason.keys)
            .distinct()
            .mapNotNull { seasonsById[it] }
            .sortedByDescending { it.startDate }
            .map { season ->
                val rows = rowsBySeason[season.id].orEmpty()
                val preStats = preStatsBySeason[season.id].orEmpty()
                PlayerSeason(
                    seasonId = season.id,
                    seasonName = season.name,
                    teamNames = (rows.map { it.first } + preStats).distinct().sorted(),
                    matches = rows.map { it.second }.sortedByDescending { it.dateTime },
                    preStatsMatchCount = preStats.size
                )
            }

        PlayerSeasonStats(
            playerId = playerId,
            seasons = seasons,
            currentSeasonId = seasons.firstOrNull { seasonsById[it.seasonId]?.active == true }?.seasonId
                ?: seasons.firstOrNull()?.seasonId,
            // Staff of any team he played for, or of an age group he's in (so a coach sees the staff
            // layout even before any match is rated).
            canSeeRatings = canRateByTeam.values.any { it } ||
                registeredAgeGroupIds.any { canSeeRatings(teamId = null, ageGroupId = it) }
        )
    }
}
