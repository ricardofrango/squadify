package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubHome
import com.ricardofrango.squadify.model.ClubHomeAlert
import com.ricardofrango.squadify.model.ClubHomeAlertMatch
import com.ricardofrango.squadify.model.ClubHomeAlertType
import com.ricardofrango.squadify.model.ClubHomeNextUp
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.monthStartOf
import com.ricardofrango.squadify.model.mondayOf
import com.ricardofrango.squadify.model.planFor
import com.ricardofrango.squadify.model.defaultMinAgeGroupRole
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
    private val clubPermissionRepository: ClubPermissionRepository,
    private val opponentRepository: OpponentRepository,
    private val userRepository: UserRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val trainingPlanRepository: TrainingPlanRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    private val trainingRatingRepository: TrainingRatingRepository
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
                callUpsTotal = callUps.size,
                opponentLogoUrl = opponentsById[next.opponentId]?.logoUrl
            )
        }.sortedBy { it.dateTime }

        // ---- Needs you ----
        val ageGroups = ageGroupRepository.getAgeGroupsByClub(clubId)
            .let { all -> if (season != null) all.filter { it.seasonId == season.id } else all }
        val alerts = mutableListOf<ClubHomeAlert>()

        if (isAdmin || role.level >= ClubRole.COORDINATOR.level) {
            ageGroups.forEach { ag ->
                val hasCoach = ageGroupRepository.getAgeGroupStaff(ag.id).any { it.role == AgeGroupRole.COACH }
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

        // Who sees which alert. Admins/coordinators (and platform admins) see everything pending in
        // the club. Age-group staff see their own age groups' alerts, but only for work their role is
        // allowed to do under the club's permission settings: MANAGE_CALL_UPS for unsent call-ups,
        // MANAGE_MATCH for pending reports (both Coach by default; see ConfigurablePermission).
        val seesWholeClub = isAdmin || role.level >= ClubRole.COORDINATOR.level
        val seasonTeams = teams.filter { team -> ageGroups.any { it.id == team.ageGroupId } }
        val staffRoleByAgeGroup: Map<String, AgeGroupRole?> =
            if (seesWholeClub) emptyMap() else ageGroups.associate { it.id to ageGroupRepository.getUserRoleInAgeGroup(userId, it.id) }

        // One lookup per setting. An override row with a null role means coordinator-only.
        suspend fun minStaffRole(permission: ConfigurablePermission): AgeGroupRole? {
            val override = clubPermissionRepository.getOverride(clubId, permission)
            return if (override != null) override.minAgeGroupRole else permission.defaultMinAgeGroupRole
        }

        suspend fun alertTeamIds(permission: ConfigurablePermission): Set<String> {
            if (seesWholeClub) return seasonTeams.map { it.id }.toSet()
            val min = minStaffRole(permission) ?: return emptySet()
            return ageGroups
                .filter { ag -> staffRoleByAgeGroup[ag.id]?.let { it.level >= min.level } == true }
                .flatMap { ag -> teams.filter { it.ageGroupId == ag.id } }
                .map { it.id }
                .toSet()
        }

        val teamNames = teams.associate { it.id to it.name }
        fun opponentLabel(opponentId: String) = opponentsById[opponentId]?.let { it.shortName ?: it.name } ?: "—"

        val callUpTeamIds = alertTeamIds(ConfigurablePermission.MANAGE_CALL_UPS)
        if (callUpTeamIds.isNotEmpty()) {
            val cutoff = now.plus(callUpWindow)
            allMatches
                .filter {
                    it.teamId in callUpTeamIds &&
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
                        date = date,
                        matches = matches.sortedBy { it.dateTime }.map {
                            ClubHomeAlertMatch(it.id, teamNames[it.teamId] ?: "—", opponentLabel(it.opponentId))
                        }
                    )
                }
        }

        // Finished matches still waiting on their report, oldest first (the longest-overdue on top,
        // per the design). Only matches with a full-time minute: those finished before post-match stats
        // existed have no stats to rate, and shouldn't flood the list.
        val reportTeamIds = alertTeamIds(ConfigurablePermission.MANAGE_MATCH)
        if (reportTeamIds.isNotEmpty()) {
            val finished = allMatches.filter {
                it.teamId in reportTeamIds && it.status == MatchStatus.FINISHED && it.fullTimeMinute != null
            }
            val completed = matchReportRepository.getCompletedMatchIds(finished.map { it.id })
            finished
                .filter { it.id !in completed }
                .sortedBy { it.dateTime }
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

        // Training plans missing for the period we're in (design 40g): the season from its first day, the
        // month from the 1st, the week from Monday -- for the age groups whose training this user may edit.
        if (season != null && today >= season.startDate && today <= season.endDate) {
            val planAgeGroups = if (seesWholeClub) ageGroups else {
                val min = minStaffRole(ConfigurablePermission.MANAGE_TRAINING)
                if (min == null) emptyList() else ageGroups.filter { ag -> staffRoleByAgeGroup[ag.id]?.let { it.level >= min.level } == true }
            }
            val plans = trainingPlanRepository.getByAgeGroups(planAgeGroups.map { it.id }).groupBy { it.ageGroupId }
            val periods = listOf(
                ClubHomeAlertType.SEASON_PLAN_MISSING to (TrainingPlanLevel.SEASON to season.startDate),
                ClubHomeAlertType.MONTH_PLAN_MISSING to (TrainingPlanLevel.MONTH to monthStartOf(today)),
                ClubHomeAlertType.WEEK_PLAN_MISSING to (TrainingPlanLevel.WEEK to mondayOf(today))
            )
            planAgeGroups.forEach { ag ->
                val agPlans = plans[ag.id].orEmpty()
                periods.forEach { (type, period) ->
                    val (level, start) = period
                    if (agPlans.planFor(level, start) == null) {
                        alerts += ClubHomeAlert(type = type, count = 1, ageGroupId = ag.id, ageGroupName = ag.name, date = start)
                    }
                }
            }
        }

        // Training sessions that ended with players still unrated (design 39i), oldest first. Rating is open
        // to every coach and staff member of the age group, so any staff role sees its own age groups'.
        val rateAgeGroups = if (seesWholeClub) ageGroups else ageGroups.filter { staffRoleByAgeGroup[it.id] != null }
        val nowLocal = now.toLocalDateTime(tz)
        val pendingSessions = rateAgeGroups.flatMap { ag ->
            trainingSessionRepository.getByAgeGroup(ag.id)
                .map { it.session }
                .filter { it.ratingsOpen(nowLocal) }
                .map { ag to it }
        }
        if (pendingSessions.isNotEmpty()) {
            val ratings = trainingRatingRepository.getForSessions(pendingSessions.map { it.second.id })
            val poolSizes = rateAgeGroups.associate { it.id to playerRepository.getPlayersByAgeGroup(it.id).size }
            pendingSessions
                .filter { (ag, s) -> (poolSizes[ag.id] ?: 0) > (ratings[s.id]?.size ?: 0) }
                .sortedWith(compareBy({ it.second.date }, { it.second.startTime }))
                .forEach { (ag, s) ->
                    alerts += ClubHomeAlert(
                        type = ClubHomeAlertType.TRAINING_RATINGS_PENDING,
                        count = ratings[s.id]?.size ?: 0,
                        total = poolSizes[ag.id],
                        ageGroupId = ag.id,
                        ageGroupName = ag.name,
                        date = s.date,
                        sessionId = s.id
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
