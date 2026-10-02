package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchOutcome
import com.ricardofrango.squadify.model.TeamMatchResult
import com.ricardofrango.squadify.model.TeamRole
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
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): AgeGroupDetails = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
            ?: throw NotFoundException("Age group not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = ageGroup.clubId,
            minClubRole = ClubRole.MEMBER,
            minTeamRole = TeamRole.STAFF,
            ageGroupId = ageGroupId,
            allowPlayerOrParentAccess = true
        )

        val club = clubRepository.getClubById(ageGroup.clubId)
            ?: throw NotFoundException("Club not found")
        val season = seasonRepository.getSeasonById(ageGroup.seasonId)
            ?: throw NotFoundException("Season not found")

        val teams = teamRepository.getTeamsByClub(ageGroup.clubId)
            .filter { it.ageGroupId == ageGroupId }

        val now = Clock.System.now()
        val upcomingMatches = teams
            .flatMap { matchRepository.getMatchesByTeam(it.id) }
            .filter {
                (it.status == MatchStatus.SCHEDULED && it.dateTime >= now) ||
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
        val upcomingMatchScores = upcomingMatches
            .filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME }
            .associate { it.id to matchEventRepository.getScoreByMatch(it.id) }

        // Add staff is the one remaining permanently coordinator+/admin-only action here -- not club
        // configurable (see TECHNICAL_DECISIONS.md §35's bucket 2), so this is a plain literal check,
        // unlike the configurable ones below (create team moved out of this fixed bucket in §37).
        val canAddStaff = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = ageGroup.clubId,
                minClubRole = ClubRole.COORDINATOR
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        suspend fun canDo(permission: ConfigurablePermission): Boolean = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = ageGroup.clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = permission,
                ageGroupId = ageGroupId
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        AgeGroupDetails(
            ageGroup = ageGroup,
            club = club,
            season = season,
            players = playerRepository.getPlayersByAgeGroup(ageGroupId),
            staff = ageGroupRepository.getAgeGroupStaff(ageGroupId),
            teams = teams,
            upcomingMatches = upcomingMatches,
            upcomingMatchScores = upcomingMatchScores,
            recentResults = recentResults,
            opponents = opponentRepository.getOpponentsByClub(ageGroup.clubId),
            canAddStaff = canAddStaff,
            // Same fixed coordinator+ gate as UpdateAgeGroupUseCase.
            canEditAgeGroup = canAddStaff,
            canCreateTeam = canDo(ConfigurablePermission.CREATE_TEAM),
            canManageMatch = canDo(ConfigurablePermission.MANAGE_MATCH),
            canAssignPlayersToAgeGroup = canDo(ConfigurablePermission.ASSIGN_PLAYERS_TO_AGE_GROUP)
        )
    }
}
