package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.ClubRepository
import com.ricardofrango.clubmanager.domain.repository.MatchEventRepository
import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.domain.repository.OpponentRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.SeasonRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ForbiddenException
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.AgeGroupDetails
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.MatchStatus
import com.ricardofrango.clubmanager.model.TeamRole
import kotlin.time.Clock

/**
 * Everything attached to one age group, for its detail screen. Readable by any club member (and a
 * linked player/parent); [AgeGroupDetails.canManage] is a non-throwing probe for the COORDINATOR /
 * age-group-coach gate that guards the add/edit actions.
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
        val upcomingMatchScores = upcomingMatches
            .filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALF_TIME }
            .associate { it.id to matchEventRepository.getScoreByMatch(it.id) }

        val canManage = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = ageGroup.clubId,
                minClubRole = ClubRole.COORDINATOR,
                minTeamRole = TeamRole.COACH,
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
            opponents = opponentRepository.getOpponentsByClub(ageGroup.clubId),
            canManage = canManage
        )
    }
}
