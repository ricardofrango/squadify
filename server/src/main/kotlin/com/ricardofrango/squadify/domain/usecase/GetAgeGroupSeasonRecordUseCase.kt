package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
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
import com.ricardofrango.squadify.model.AgeGroupSeasonRecord
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.TeamRole

/**
 * Age Group Details' Stats and Members data (design 33): every team's season record (see
 * [buildTeamSeasonRecord]) plus the age group's pool. Results by team, the combined record, minutes
 * across the pool and each member's apps/minutes/teams are derived client-side (`SeasonStats.kt`).
 * Same read gate as Age Group Details. Ratings (only the team cards' averages use them) follow the
 * match report's gate -- coordinators/admins and this age group's staff -- and are stripped otherwise.
 */
class GetAgeGroupSeasonRecordUseCase(
    private val ageGroupRepository: AgeGroupRepository,
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
    suspend operator fun invoke(userId: String, ageGroupId: String): AgeGroupSeasonRecord = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = ageGroup.clubId,
            minClubRole = ClubRole.MEMBER,
            minTeamRole = TeamRole.STAFF,
            ageGroupId = ageGroupId,
            allowPlayerOrParentAccess = true
        )

        val canSeeRatings = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = ageGroup.clubId,
                minClubRole = ClubRole.COORDINATOR,
                minTeamRole = TeamRole.STAFF,
                ageGroupId = ageGroupId
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        val season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: throw NotFoundException("Season not found")
        val opponentsById = opponentRepository.getOpponentsByClub(ageGroup.clubId).associateBy { it.id }
        val teams = teamRepository.getTeamsByClub(ageGroup.clubId)
            .filter { it.ageGroupId == ageGroupId }
            .sortedBy { it.name }

        AgeGroupSeasonRecord(
            ageGroupId = ageGroupId,
            teams = teams.map { team ->
                buildTeamSeasonRecord(
                    team = team,
                    season = season,
                    canSeeRatings = canSeeRatings,
                    opponentsById = opponentsById,
                    matchRepository = matchRepository,
                    matchEventRepository = matchEventRepository,
                    matchPlayerStatsRepository = matchPlayerStatsRepository,
                    matchReportRepository = matchReportRepository,
                    playerRepository = playerRepository
                )
            },
            pool = playerRepository.getPlayersByAgeGroup(ageGroupId).map { it.toSeasonPlayer() },
            canSeeRatings = canSeeRatings
        )
    }
}
