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
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.ClubHomeAlertType
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlanLevel
import com.ricardofrango.squadify.model.monthStartOf
import com.ricardofrango.squadify.model.mondayOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class GetClubHomeUseCaseTest {

    private val clubRepository = mockk<ClubRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val matchRepository = mockk<MatchRepository>()
    private val matchReportRepository = mockk<MatchReportRepository>()
    init { coEvery { matchReportRepository.getCompletedMatchIds(any()) } returns emptySet() }
    private val opponentRepository = mockk<OpponentRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val trainingPlanRepository = mockk<TrainingPlanRepository>()
    private val useCase = GetClubHomeUseCase(
        clubRepository, teamRepository, playerRepository, seasonRepository, ageGroupRepository,
        matchRepository, matchReportRepository, clubPermissionRepository, opponentRepository, userRepository, checkUserPermissionUseCase, transactionManager,
        trainingPlanRepository
    )
    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    // Every age group has its season, this month's and this week's plan unless a test says otherwise,
    // so the other tests only see the alerts they're about.
    init {
        coEvery { trainingPlanRepository.getByAgeGroups(any()) } answers {
            firstArg<List<String>>().flatMap { ag ->
                listOf(
                    TrainingPlan("s-$ag", ag, TrainingPlanLevel.SEASON, LocalDate(2026, 7, 1)),
                    TrainingPlan("m-$ag", ag, TrainingPlanLevel.MONTH, monthStartOf(today), focus = "f"),
                    TrainingPlan("w-$ag", ag, TrainingPlanLevel.WEEK, mondayOf(today), focus = "f")
                )
            }
        }
    }

    private val clubId = "club-1"
    private val seasonId = "season-1"
    private val ag1 = "ag-1"
    private val ag2 = "ag-2"

    private val club = Club(clubId, "CD Ribamar", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)
    private val season = Season(seasonId, clubId, "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 7, 1), LocalDate(2027, 6, 30), active = true)
    private val teamA = Team("t-a", clubId, ag1, seasonId, "Sub-15 A", "A", true)
    private val teamB = Team("t-b", clubId, ag2, seasonId, "Sub-17 A", "A", true)

    private fun user(id: String, admin: Boolean = false) = User(
        id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE,
        isPlatformAdmin = admin, createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun match(id: String, teamId: String, offset: kotlin.time.Duration) = Match(
        id = id, clubId = clubId, teamId = teamId, opponentId = "opp-1",
        dateTime = Clock.System.now() + offset, status = MatchStatus.SCHEDULED
    )

    private fun baseStubs(userId: String, role: ClubRole?) {
        coEvery { clubRepository.getClubById(clubId) } returns club
        coEvery { userRepository.findById(userId) } returns user(userId)
        coEvery { userRepository.getUserRoleInClub(userId, clubId) } returns role
        coEvery { seasonRepository.getActiveSeasonsByClub(clubId) } returns listOf(season)
        coEvery { seasonRepository.getSeasonsByClub(clubId) } returns listOf(season)
        coEvery { teamRepository.getTeamsByClub(clubId) } returns listOf(teamA, teamB)
        coEvery { opponentRepository.getOpponentsByClub(clubId) } returns
            listOf(Opponent("opp-1", clubId, "AD Fontelas", "AD Fontelas", null))
        coEvery { playerRepository.countPlayersByClub(clubId) } returns 118
        coEvery { ageGroupRepository.countCoachesByClub(clubId) } returns 9
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubId) } returns listOf(
            AgeGroup(ag1, clubId, "Sub-15", seasonId, 2011, 2011),
            AgeGroup(ag2, clubId, "Sub-17", seasonId, 2009, 2009)
        )
        // permission fall-throughs for non-admin, non-coordinator paths
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(userId, any()) } returns null
        coEvery { playerRepository.getPlayersByUser(userId) } returns emptyList()
    }

    @Test
    fun countsAndNextUpForAnAdmin() = runBlocking {
        val u = "admin"
        baseStubs(u, ClubRole.CLUB_ADMIN)
        val soon = match("m-a", teamA.id, 2.days)
        coEvery { matchRepository.getMatchesByClub(clubId) } returns listOf(soon)
        coEvery { matchRepository.getMatchesByTeam(teamA.id) } returns listOf(soon)
        coEvery { matchRepository.getMatchesByTeam(teamB.id) } returns emptyList()
        coEvery { matchRepository.getCallUpsForMatch("m-a") } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupStaff(any()) } returns
            listOf(AgeGroupStaffMember("c1", "Nuno", "F", TeamRole.COACH))

        val home = useCase(u, clubId)

        assertEquals(2, home.teamCount)
        assertEquals(118, home.playerCount)
        assertEquals(9, home.coachCount)
        assertEquals("26/27", home.seasonName)
        // Only teamA has an upcoming match -> exactly one Next up row, none for teamB.
        assertEquals(listOf("Sub-15 A"), home.nextUp.map { it.teamName })
        assertEquals("AD Fontelas", home.nextUp.first().opponentName)
        // Admins see everything pending in the club: the in-window match has no call-ups yet.
        assertEquals(listOf(ClubHomeAlertType.CALLUPS_UNSENT), home.alerts.map { it.type })
    }

    @Test
    fun coordinatorSeesAgeGroupsWithoutACoach() = runBlocking {
        val u = "coord"
        baseStubs(u, ClubRole.COORDINATOR)
        coEvery { matchRepository.getMatchesByClub(clubId) } returns emptyList()
        coEvery { matchRepository.getMatchesByTeam(any()) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupStaff(ag1) } returns
            listOf(AgeGroupStaffMember("c1", "Nuno", "F", TeamRole.COACH))
        coEvery { ageGroupRepository.getAgeGroupStaff(ag2) } returns emptyList()

        val alerts = useCase(u, clubId).alerts

        assertEquals(1, alerts.size)
        assertEquals(ClubHomeAlertType.AGE_GROUP_NEEDS_COACH, alerts.first().type)
        assertEquals("Sub-17", alerts.first().ageGroupName)
    }

    @Test
    fun coachSeesMatchesInsideFiveDaysWithNoCallUps() = runBlocking {
        val u = "coach"
        baseStubs(u, ClubRole.MEMBER)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(u, ag1) } returns TeamRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(u, ag2) } returns null
        coEvery { ageGroupRepository.getAgeGroupStaff(any()) } returns emptyList()

        val within = match("m-in", teamA.id, 3.days)
        val beyond = match("m-out", teamA.id, 9.days)
        coEvery { matchRepository.getMatchesByClub(clubId) } returns listOf(within, beyond)
        coEvery { matchRepository.getMatchesByTeam(teamA.id) } returns listOf(within, beyond)
        coEvery { matchRepository.getMatchesByTeam(teamB.id) } returns emptyList()
        coEvery { matchRepository.getCallUpsForMatch("m-in") } returns emptyList()
        coEvery { matchRepository.getCallUpsForMatch("m-out") } returns emptyList()

        val alerts = useCase(u, clubId).alerts

        // Plain member is not a coordinator, so no AGE_GROUP_NEEDS_COACH; only the in-window match.
        assertEquals(1, alerts.size)
        assertEquals(ClubHomeAlertType.CALLUPS_UNSENT, alerts.first().type)
        assertEquals(1, alerts.first().count)
        // The match behind the to-do, so tapping it can open the match.
        assertEquals(listOf("m-in"), alerts.first().matches.map { it.matchId })
        assertEquals(teamA.name, alerts.first().matches.single().teamName)
    }

    @Test
    fun coachSeesFinishedMatchesWhoseReportIsNotCompleted() = runBlocking {
        val u = "coach"
        baseStubs(u, ClubRole.MEMBER)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(u, ag1) } returns TeamRole.COACH
        val pending = match("m-pending", teamA.id, (-2).days).copy(status = MatchStatus.FINISHED, fullTimeMinute = 92)
        val done = match("m-done", teamA.id, (-9).days).copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        // Finished before post-match stats existed: no full-time minute, no to-do.
        val legacy = match("m-legacy", teamA.id, (-30).days).copy(status = MatchStatus.FINISHED)
        // Another age group's match: not this coach's to-do.
        val otherTeam = match("m-other", teamB.id, (-1).days).copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        coEvery { matchRepository.getMatchesByClub(clubId) } returns listOf(pending, done, legacy, otherTeam)
        coEvery { matchRepository.getMatchesByTeam(any()) } returns emptyList()
        coEvery { matchReportRepository.getCompletedMatchIds(listOf("m-pending", "m-done")) } returns setOf("m-done")

        val alerts = useCase(u, clubId).alerts.filter { it.type == ClubHomeAlertType.MATCH_REPORT_PENDING }

        assertEquals(listOf("m-pending"), alerts.map { it.matchId })
        assertEquals("Sub-15 A", alerts.single().teamName)
        assertEquals("AD Fontelas", alerts.single().opponentName)
    }

    @Test
    fun aCoordinatorSeesEveryPendingReportInTheClubWithoutCoachingAnything() = runBlocking {
        val u = "coord"
        baseStubs(u, ClubRole.COORDINATOR)
        coEvery { ageGroupRepository.getAgeGroupStaff(any()) } returns
            listOf(AgeGroupStaffMember("c1", "Nuno", "F", TeamRole.COACH))
        val a = match("m-a", teamA.id, (-2).days).copy(status = MatchStatus.FINISHED, fullTimeMinute = 92)
        val b = match("m-b", teamB.id, (-5).days).copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        coEvery { matchRepository.getMatchesByClub(clubId) } returns listOf(a, b)
        coEvery { matchRepository.getMatchesByTeam(any()) } returns emptyList()

        val reports = useCase(u, clubId).alerts.filter { it.type == ClubHomeAlertType.MATCH_REPORT_PENDING }

        // Both age groups, oldest first.
        assertEquals(listOf("m-b", "m-a"), reports.map { it.matchId })
    }

    @Test
    fun aPlatformAdminSeesEverythingPendingToo() = runBlocking {
        val u = "platform"
        baseStubs(u, null)
        coEvery { userRepository.findById(u) } returns user(u, admin = true)
        coEvery { ageGroupRepository.getAgeGroupStaff(any()) } returns
            listOf(AgeGroupStaffMember("c1", "Nuno", "F", TeamRole.COACH))
        val pending = match("m-p", teamB.id, (-1).days).copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        val noCallUps = match("m-s", teamA.id, 2.days)
        coEvery { matchRepository.getMatchesByClub(clubId) } returns listOf(pending, noCallUps)
        coEvery { matchRepository.getMatchesByTeam(any()) } returns emptyList()
        coEvery { matchRepository.getCallUpsForMatch("m-s") } returns emptyList()

        val types = useCase(u, clubId).alerts.map { it.type }

        assertEquals(listOf(ClubHomeAlertType.CALLUPS_UNSENT, ClubHomeAlertType.MATCH_REPORT_PENDING), types)
    }

    @Test
    fun ageGroupStaffDontSeeReportsByDefault() = runBlocking {
        // MANAGE_MATCH defaults to Coach, so a staff member can't fill the report and isn't nagged.
        val u = "staff"
        baseStubs(u, ClubRole.MEMBER)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(u, ag1) } returns TeamRole.STAFF
        val pending = match("m-p", teamA.id, (-1).days).copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        coEvery { matchRepository.getMatchesByClub(clubId) } returns listOf(pending)
        coEvery { matchRepository.getMatchesByTeam(any()) } returns emptyList()

        assertTrue(useCase(u, clubId).alerts.none { it.type == ClubHomeAlertType.MATCH_REPORT_PENDING })
    }

    @Test
    fun ageGroupStaffSeeReportsWhenTheClubOpensManageMatchToStaff() = runBlocking {
        val u = "staff"
        baseStubs(u, ClubRole.MEMBER)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(u, ag1) } returns TeamRole.STAFF
        coEvery { clubPermissionRepository.getOverride(clubId, ConfigurablePermission.MANAGE_MATCH) } returns
            ClubPermissionSetting(ConfigurablePermission.MANAGE_MATCH, TeamRole.STAFF)
        val mine = match("m-mine", teamA.id, (-1).days).copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        val other = match("m-other", teamB.id, (-1).days).copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        coEvery { matchRepository.getMatchesByClub(clubId) } returns listOf(mine, other)
        coEvery { matchRepository.getMatchesByTeam(any()) } returns emptyList()

        val reports = useCase(u, clubId).alerts.filter { it.type == ClubHomeAlertType.MATCH_REPORT_PENDING }

        assertEquals(listOf("m-mine"), reports.map { it.matchId })
    }

    @Test
    fun aCoachDoesntSeeReportsWhenTheClubMakesThemCoordinatorOnly() = runBlocking {
        val u = "coach"
        baseStubs(u, ClubRole.MEMBER)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(u, ag1) } returns TeamRole.COACH
        coEvery { clubPermissionRepository.getOverride(clubId, ConfigurablePermission.MANAGE_MATCH) } returns
            ClubPermissionSetting(ConfigurablePermission.MANAGE_MATCH, null)
        val pending = match("m-p", teamA.id, (-1).days).copy(status = MatchStatus.FINISHED, fullTimeMinute = 90)
        coEvery { matchRepository.getMatchesByClub(clubId) } returns listOf(pending)
        coEvery { matchRepository.getMatchesByTeam(any()) } returns emptyList()

        assertTrue(useCase(u, clubId).alerts.none { it.type == ClubHomeAlertType.MATCH_REPORT_PENDING })
    }

    private fun quietMatches() {
        coEvery { matchRepository.getMatchesByClub(clubId) } returns emptyList()
        coEvery { matchRepository.getMatchesByTeam(any()) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupStaff(any()) } returns listOf(AgeGroupStaffMember("c1", "Nuno", "F", TeamRole.COACH))
    }

    @Test
    fun aCoordinatorIsToldAboutEveryMissingPlanOfTheCurrentPeriods() = runBlocking<Unit> {
        // This test needs today inside the season (1 Jul 2026 – 30 Jun 2027).
        if (today < LocalDate(2026, 7, 1) || today > LocalDate(2027, 6, 30)) return@runBlocking
        val u = "coordinator"
        baseStubs(u, ClubRole.COORDINATOR)
        quietMatches()
        // Sub-15 has only its season plan; Sub-17 has nothing.
        coEvery { trainingPlanRepository.getByAgeGroups(any()) } returns
            listOf(TrainingPlan("s", ag1, TrainingPlanLevel.SEASON, LocalDate(2026, 7, 1)))

        val alerts = useCase(u, clubId).alerts

        assertEquals(
            listOf(
                ClubHomeAlertType.MONTH_PLAN_MISSING to ag1, ClubHomeAlertType.WEEK_PLAN_MISSING to ag1,
                ClubHomeAlertType.SEASON_PLAN_MISSING to ag2, ClubHomeAlertType.MONTH_PLAN_MISSING to ag2, ClubHomeAlertType.WEEK_PLAN_MISSING to ag2
            ),
            alerts.map { it.type to it.ageGroupId }
        )
        assertEquals(mondayOf(today), alerts.first { it.type == ClubHomeAlertType.WEEK_PLAN_MISSING }.date)
        assertEquals(monthStartOf(today), alerts.first { it.type == ClubHomeAlertType.MONTH_PLAN_MISSING }.date)
    }

    @Test
    fun aCoachSeesPlanAlertsOnlyForTheirOwnAgeGroup() = runBlocking<Unit> {
        if (today < LocalDate(2026, 7, 1) || today > LocalDate(2027, 6, 30)) return@runBlocking
        val u = "coach"
        baseStubs(u, ClubRole.MEMBER)
        quietMatches()
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(u, ag1) } returns TeamRole.COACH
        coEvery { trainingPlanRepository.getByAgeGroups(any()) } returns emptyList()

        val alerts = useCase(u, clubId).alerts

        assertTrue(alerts.isNotEmpty())
        assertTrue(alerts.all { it.ageGroupId == ag1 })
    }

    @Test
    fun staffDoNotSeePlanAlertsWhileTrainingIsCoachLevel() = runBlocking<Unit> {
        val u = "staff"
        baseStubs(u, ClubRole.MEMBER)
        quietMatches()
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(u, ag1) } returns TeamRole.STAFF
        coEvery { trainingPlanRepository.getByAgeGroups(any()) } returns emptyList()

        assertTrue(useCase(u, clubId).alerts.none { it.type.name.endsWith("PLAN_MISSING") })
    }
}
