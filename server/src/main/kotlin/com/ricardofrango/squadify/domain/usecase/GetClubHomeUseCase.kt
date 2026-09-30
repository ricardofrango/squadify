package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubHome
import com.ricardofrango.squadify.model.ClubHomeAlert
import com.ricardofrango.squadify.model.ClubHomeAlertType
import com.ricardofrango.squadify.model.ClubHomeNextUp
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.TeamRole
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
 * that age group's matches inside 5 days with no call-ups, and its finished matches whose report
 * isn't completed yet; both roles -> all of them).
 */
class GetClubHomeUseCase(
    private val clubRepository: ClubRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val seasonRepository: SeasonRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val matchRepository: MatchRepository,
    private val matchReportRepository: MatchReportRepository,
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
                matchId = next.id,
                teamId = team.id,
                teamName = team.name,
                opponentName = opponentsById[next.opponentId]?.let { it.shortName ?: it.name } ?: "—",
                location = next.location,
                dateTime = next.dateTime,
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

            // Finished matches still waiting on their report, most recent first. Only matches with a
            // full-time minute: those finished before post-match stats existed have no stats to rate,
            // and shouldn't flood the list.
            val finished = allMatches.filter {
                it.teamId in coachedTeamIds && it.status == MatchStatus.FINISHED && it.fullTimeMinute != null
            }
            val completed = matchReportRepository.getCompletedMatchIds(finished.map { it.id })
            val teamNames = teams.associate { it.id to it.name }
            finished
                .filter { it.id !in completed }
                .sortedByDescending { it.dateTime }
                .forEach { match ->
                    alerts += ClubHomeAlert(
                        type = ClubHomeAlertType.MATCH_REPORT_PENDING,
                        count = 1,
                        date = match.dateTime.toLocalDateTime(tz).date,
                        matchId = match.id,
                        teamName = teamNames[match.teamId],
                        opponentName = opponentsById[match.opponentId]?.let { it.shortName ?: it.name }
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
