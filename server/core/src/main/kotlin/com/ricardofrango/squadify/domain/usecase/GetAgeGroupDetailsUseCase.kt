package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.access.allowed
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.asNotStarted
import com.ricardofrango.squadify.model.isNotPlayed
import com.ricardofrango.squadify.model.MatchOutcome
import com.ricardofrango.squadify.model.TeamMatchResult
import kotlin.time.Clock

/**
 * Everything attached to one age group, for its detail screen. Readable by any club member (and a
 * linked player/parent); see `AgeGroupDetails`' own doc comment for its three independent
 * `canManageX` probes.
 */
class GetAgeGroupDetailsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val seasonRepository: SeasonRepository,
    private val playerRepository: PlayerRepository,
    private val teamRepository: TeamRepository,
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val opponentRepository: OpponentRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): AgeGroupDetails = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
            ?: throw NotFoundException("Age group not found")

        val scope = GameScope(ageGroup.clubId, ageGroupId = ageGroupId)
        accessPolicy.requireViewGames(userId, scope)

        val club = clubRepository.getClubById(ageGroup.clubId)
            ?: throw NotFoundException("Club not found")
        val season = seasonRepository.getSeasonById(ageGroup.seasonId)
            ?: throw NotFoundException("Season not found")

        val teams = teamRepository.getTeamsByClub(ageGroup.clubId)
            .filter { it.ageGroupId == ageGroupId }

        val now = Clock.System.now()
        val teamMatches = teams.flatMap { matchRepository.getMatchesByTeam(it.id) }
        // Still scheduled well past kickoff: their own "Not played" section, not Upcoming.
        val notPlayedMatches = teamMatches.filter { it.isNotPlayed(now) }.sortedByDescending { it.dateTime }
        val upcomingMatches = teamMatches
            .filter {
                (it.status == MatchStatus.SCHEDULED && !it.isNotPlayed(now)) ||
                    it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME
            }
            .sortedBy { it.dateTime }
        val recentResults = teams
            .flatMap { matchRepository.getMatchesByTeam(it.id) }
            .filter { it.status == MatchStatus.FINISHED }
            .sortedByDescending { it.dateTime }
            .map { match ->
                val score = matchEventRepository.getScoreByMatch(match.id)
                TeamMatchResult(
                    match = match,
                    ourScore = score.ourScore,
                    opponentScore = score.opponentScore,
                    outcome = when {
                        score.ourScore > score.opponentScore -> MatchOutcome.WIN
                        score.ourScore < score.opponentScore -> MatchOutcome.LOSS
                        else -> MatchOutcome.DRAW
                    }
                )
            }
        // Without live scores on the plan, a live game is listed as not started, with no score.
        val liveScores = allowed { accessPolicy.requireLiveScoresInPlan(ageGroup.clubId) }
        val upcomingMatchScores = if (!liveScores) emptyMap() else upcomingMatches
            .filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME }
            .associate { it.id to matchEventRepository.getScoreByMatch(it.id) }


        AgeGroupDetails(
            ageGroup = ageGroup,
            club = club,
            season = season,
            players = playerRepository.getPlayersByAgeGroup(ageGroupId),
            staff = ageGroupRepository.getAgeGroupStaff(ageGroupId),
            teams = teams,
            notPlayedMatches = notPlayedMatches,
            upcomingMatches = if (liveScores) upcomingMatches else upcomingMatches.map { it.asNotStarted() },
            upcomingMatchScores = upcomingMatchScores,
            recentResults = recentResults,
            opponents = opponentRepository.getOpponentsByClub(ageGroup.clubId),
            // Each the gate of the action it shows, so a button only appears where it would work.
            canAddStaff = allowed { accessPolicy.requireManageStaff(userId, scope) },
            canEditAgeGroup = allowed { accessPolicy.requireEditAgeGroup(userId, scope) },
            canCreateTeam = allowed { accessPolicy.requireManageTeams(userId, scope) },
            canManageMatch = allowed { accessPolicy.requireManageMatch(userId, scope) },
            canAssignPlayersToAgeGroup = allowed { accessPolicy.requireManagePool(userId, scope) }
        )
    }
}
