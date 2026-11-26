package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.GameScope
import com.sportssoftw.squadcall.domain.access.allowed
import com.sportssoftw.squadcall.domain.access.scope
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.MatchEventRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.asNotStarted
import com.sportssoftw.squadcall.model.isNotPlayed
import com.sportssoftw.squadcall.model.MatchOutcome
import com.sportssoftw.squadcall.model.MatchStatus
import com.sportssoftw.squadcall.model.TeamDetails
import com.sportssoftw.squadcall.model.TeamMatchResult
import kotlin.time.Clock

/**
 * Everything attached to one team, for its detail screen. A team is a scheduling handle under an age
 * group, so this returns its fixtures ([TeamDetails.upcomingMatches] / [TeamDetails.recentResults])
 * plus a read-only view of the age group's pool size and staff. Readable by any club member (and a
 * linked player/parent of a player in the age-group pool). Each `can*` flag is a non-throwing probe
 * of the check its action enforces, e.g. [TeamDetails.canManageMatch] the one scheduling a match
 * makes (see `CreateMatchUseCase`).
 */
class GetTeamDetailsUseCase(
    private val teamRepository: TeamRepository,
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val opponentRepository: OpponentRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, teamId: String): TeamDetails = transactionManager.runInTransaction {
        val team = teamRepository.getTeamById(teamId)
            ?: throw NotFoundException("Team not found")

        accessPolicy.requireViewGames(userId, team.scope)

        val club = clubRepository.getClubById(team.clubId)
            ?: throw NotFoundException("Club not found")
        val ageGroup = ageGroupRepository.getAgeGroupById(team.ageGroupId)
            ?: throw NotFoundException("Age group not found")
        val season = seasonRepository.getSeasonById(team.seasonId)
            ?: throw NotFoundException("Season not found")

        val canManageMatch = allowed { accessPolicy.requireManageMatch(userId, team.scope) }
        // Staff chips only link where Member details opens.
        val canViewMembers = allowed { accessPolicy.requireViewMembers(userId, team.clubId) }
        // UpdateTeamUseCase's and DeleteTeamUseCase's checks, so each button only shows where it would work.
        val canEditTeam = allowed { accessPolicy.requireEditTeam(userId, team.scope) }
        val canDeleteTeam = allowed { accessPolicy.requireManageTeams(userId, GameScope(team.clubId, ageGroupId = team.ageGroupId)) }

        val now = Clock.System.now()
        val allMatches = matchRepository.getMatchesByTeam(teamId)

        // Still scheduled well past kickoff: their own "Not played" section, not Upcoming.
        val notPlayedMatches = allMatches.filter { it.isNotPlayed(now) }.sortedByDescending { it.dateTime }
        val upcomingMatches = allMatches
            .filter {
                (it.status == MatchStatus.SCHEDULED && !it.isNotPlayed(now)) ||
                    it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME
            }
            .sortedBy { it.dateTime }
        // Without live scores on the plan, a live game is listed as not started, with no score.
        val liveScores = allowed { accessPolicy.requireLiveInPlan(team.clubId) }
        val upcomingMatchScores = if (!liveScores) emptyMap() else upcomingMatches
            .filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME }
            .associate { it.id to matchEventRepository.getScoreByMatch(it.id) }

        // Finished matches, newest first, each paired with its scoreline (rebuilt from match events).
        val recentResults = allMatches
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

        val siblingTeamNames = teamRepository.getTeamsByClub(team.clubId)
            .filter { it.ageGroupId == team.ageGroupId && it.id != team.id }
            .map { it.name }

        TeamDetails(
            team = team,
            club = club,
            ageGroup = ageGroup,
            season = season,
            poolSize = playerRepository.getPlayersByAgeGroup(team.ageGroupId).size,
            siblingTeamNames = siblingTeamNames,
            staff = ageGroupRepository.getAgeGroupStaff(team.ageGroupId),
            notPlayedMatches = notPlayedMatches,
            upcomingMatches = if (liveScores) upcomingMatches else upcomingMatches.map { it.asNotStarted() },
            upcomingMatchScores = upcomingMatchScores,
            recentResults = recentResults,
            opponents = opponentRepository.getOpponentsByClub(team.clubId),
            canManageMatch = canManageMatch,
            canViewMembers = canViewMembers,
            canEditTeam = canEditTeam,
            canDeleteTeam = canDeleteTeam,
            matchCount = allMatches.size
        )
    }
}
