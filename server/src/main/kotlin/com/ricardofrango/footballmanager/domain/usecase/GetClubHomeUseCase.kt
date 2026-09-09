package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.CallUpStatus
import com.ricardofrango.footballmanager.model.ClubHome
import com.ricardofrango.footballmanager.model.ClubHomeAlert
import com.ricardofrango.footballmanager.model.ClubHomeAlertType
import com.ricardofrango.footballmanager.model.ClubHomeNextUp
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.MatchStatus
import com.ricardofrango.footballmanager.model.TeamRole
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * The aggregate behind the club Home tab: stat-grid counts, one "Next up" row per team that has an
 * upcoming match, and the role-scoped "Needs you" alerts. Readable by any club member; the alert set
 * depends on the caller's roles (COORDINATOR+ -> age groups with no coach; a coach of an age group ->
 * that age group's matches inside 5 days with no call-ups; both roles -> both).
 */
class GetClubHomeUseCase(
    private val clubRepository: ClubRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val seasonRepository: SeasonRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val matchRepository: MatchRepository,
    private val opponentRepository: OpponentRepository,
    private val userRepository: UserRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    private val callUpWindow = 5.days

    suspend operator fun invoke(userId: String, clubId: String): ClubHome = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.MEMBER,
            allowPlayerOrParentAccess = true
        )

        val club = clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")
        val role = userRepository.getUserRoleInClub(userId, clubId) ?: ClubRole.MEMBER
        val isAdmin = userRepository.findById(userId)?.isPlatformAdmin == true

        // The season everything is scoped to: the active one, else the most recently ending.
        val season = seasonRepository.getActiveSeasonsByClub(clubId).firstOrNull()
            ?: seasonRepository.getSeasonsByClub(clubId).maxByOrNull { it.endDate }

        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date
        val weekStart = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        val weekEndExclusive = weekStart.plus(7, DateTimeUnit.DAY)

        val teams = teamRepository.getTeamsByClub(clubId)
        val allMatches = matchRepository.getMatchesByClub(clubId)
        val opponentsById = opponentRepository.getOpponentsByClub(clubId).associateBy { it.id }

        val matchesThisWeek = allMatches.count {
            val d = it.dateTime.toLocalDateTime(tz).date
            d >= weekStart && d < weekEndExclusive
        }

        // ---- Next up: soonest scheduled future match per team ----
        val nextUp = teams.mapNotNull { team ->
            val next = matchRepository.getMatchesByTeam(team.id)
                .filter { it.status == MatchStatus.SCHEDULED && it.dateTime >= now }
                .minByOrNull { it.dateTime }
                ?: return@mapNotNull null
            val callUps = matchRepository.getCallUpsForMatch(next.id)
            ClubHomeNextUp(
                teamId = team.id,
                teamName = team.name,
                opponentName = opponentsById[next.opponentId]?.let { it.shortName ?: it.name } ?: "—",
                location = next.location,
                dateTime = next.dateTime,
                callUpsConfirmed = callUps.count { it.callUp.status == CallUpStatus.CONFIRMED },
                callUpsTotal = callUps.size
            )
        }.sortedBy { it.dateTime }

        // ---- Needs you ----
        val ageGroups = ageGroupRepository.getAgeGroupsByClub(clubId)
            .let { all -> if (season != null) all.filter { it.seasonId == season.id } else all }
        val alerts = mutableListOf<ClubHomeAlert>()

        if (isAdmin || role.level >= ClubRole.COORDINATOR.level) {
            ageGroups.forEach { ag ->
                val hasCoach = ageGroupRepository.getAgeGroupStaff(ag.id).any { it.role == TeamRole.COACH }
                if (!hasCoach) {
                    alerts += ClubHomeAlert(
                        type = ClubHomeAlertType.AGE_GROUP_NEEDS_COACH,
                        count = 1,
                        ageGroupId = ag.id,
                        ageGroupName = ag.name
                    )
                }
            }
        }

        val coachedTeamIds = ageGroups
            .filter { ageGroupRepository.getUserRoleInAgeGroup(userId, it.id) == TeamRole.COACH }
            .flatMap { ag -> teams.filter { it.ageGroupId == ag.id } }
            .map { it.id }
            .toSet()
        if (coachedTeamIds.isNotEmpty()) {
            val cutoff = now.plus(callUpWindow)
            allMatches
                .filter {
                    it.teamId in coachedTeamIds &&
                        it.status == MatchStatus.SCHEDULED &&
                        it.dateTime >= now && it.dateTime <= cutoff &&
                        matchRepository.getCallUpsForMatch(it.id).isEmpty()
                }
                .groupBy { it.dateTime.toLocalDateTime(tz).date }
                .toSortedMap()
                .forEach { (date, matches) ->
                    alerts += ClubHomeAlert(
                        type = ClubHomeAlertType.CALLUPS_UNSENT,
                        count = matches.size,
                        date = date
                    )
                }
        }

        ClubHome(
            club = club,
            seasonName = season?.name,
            role = role,
            teamCount = teams.size,
            playerCount = playerRepository.countPlayersByClub(clubId).toInt(),
            coachCount = ageGroupRepository.countCoachesByClub(clubId).toInt(),
            matchesThisWeek = matchesThisWeek,
            nextUp = nextUp,
            alerts = alerts
        )
    }
}
