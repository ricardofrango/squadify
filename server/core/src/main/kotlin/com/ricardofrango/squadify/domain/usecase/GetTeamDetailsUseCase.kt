package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.access.allowed
import com.ricardofrango.squadify.domain.access.scope
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
import com.ricardofrango.squadify.model.isNotPlayed
import com.ricardofrango.squadify.model.MatchOutcome
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.TeamDetails
import com.ricardofrango.squadify.model.TeamMatchResult
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
        val upcomingMatchScores = upcomingMatches
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
            upcomingMatches = upcomingMatches,
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
