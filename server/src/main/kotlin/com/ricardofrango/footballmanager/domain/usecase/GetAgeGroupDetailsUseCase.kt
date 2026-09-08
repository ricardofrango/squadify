package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.AgeGroupDetails
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.MatchStatus
import com.ricardofrango.footballmanager.model.TeamRole
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
            .filter { it.dateTime >= now && it.status == MatchStatus.SCHEDULED }
            .sortedBy { it.dateTime }

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
            opponents = opponentRepository.getOpponentsByClub(ageGroup.clubId),
            canManage = canManage
        )
    }
}
