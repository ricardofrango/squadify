package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.MatchOutcome
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * A team's detail is now age-group-scoped: the permission check resolves the team to its age group,
 * staff/pool come from the age group, and the team itself contributes only its fixture list.
 * `canManageMatch` is a non-throwing probe of `ConfigurablePermission.MANAGE_MATCH` -- defaults to
 * coach, same as these tests' expectations, but is club-configurable (see `CreateMatchUseCase`).
 */
class GetTeamDetailsUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val matchRepository = mockk<MatchRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
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
    private val useCase = GetTeamDetailsUseCase(
        teamRepository, clubRepository, ageGroupRepository, seasonRepository, playerRepository,
        matchRepository, matchEventRepository, opponentRepository, checkUserPermissionUseCase,
        transactionManager
    )

    private val clubIdA = "club-a"
    private val teamId = "team-1"
    private val ag1 = "age-group-1"

    private val team = Team(
        id = teamId, clubId = clubIdA, ageGroupId = ag1, seasonId = "season-1",
        name = "Sub-15 A", code = "S15A", active = true
    )
    private val club = Club(
        id = clubIdA, name = "CD Ribamar", status = ClubStatus.ACTIVE,
        createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL
    )
    private val ageGroup = AgeGroup(
        id = ag1, clubId = clubIdA, name = "Sub-15", seasonId = "season-1", startYear = 2011, endYear = 2011
    )
    private val season = Season(
        id = "season-1", clubId = clubIdA, name = "26/27", type = SeasonType.EUROPEAN_YEAR,
        startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2027, 6, 30), active = true
    )

    private fun user(id: String) = User(
        id = id, email = "$id@test.com", firstName = "Test", lastName = "User",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    /** Stubs shared by every test: the team resolves, and every downstream repo returns empty. */
    private fun stubResolvedTeamWithNoData() {
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns ageGroup
        coEvery { seasonRepository.getSeasonById("season-1") } returns season
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team)
        coEvery { matchRepository.getMatchesByTeam(teamId) } returns emptyList()
        coEvery { playerRepository.getPlayersByAgeGroup(ag1) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupStaff(ag1) } returns emptyList()
        coEvery { opponentRepository.getOpponentsByClub(clubIdA) } returns emptyList()
    }

    @Test
    fun clubAdminSeesTeamDetailsAndCanManage() = runBlocking {
        val admin = user("p2-club-admin")
        stubResolvedTeamWithNoData()
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN

        val result = useCase(admin.id, teamId)

        assertEquals("Sub-15", result.ageGroup.name)
        assertEquals("26/27", result.season.name)
        assertTrue(result.canManageMatch)
        assertTrue(result.canViewMembers)
    }

    @Test
    fun coachOfTheAgeGroupCanSeeAndManage() = runBlocking {
        val coach = user("p5-coach-ag1")
        stubResolvedTeamWithNoData()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH

        assertTrue(useCase(coach.id, teamId).canManageMatch)
        // A coach isn't a coordinator, so the staff chips don't open Member details.
        assertFalse(useCase(coach.id, teamId).canViewMembers)
    }

    @Test
    fun ageGroupStaffCanSeeButNotManage() = runBlocking {
        val staff = user("p6-staff-ag1")
        stubResolvedTeamWithNoData()
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns TeamRole.STAFF

        assertFalse(useCase(staff.id, teamId).canManageMatch)
    }

    @Test
    fun parentOfAPlayerInTheAgeGroupCanSeeButNotManage() = runBlocking {
        val parent = user("p10-parent")
        val child = Player(
            id = "child-1", clubId = clubIdA, firstName = "Test", lastName = "Child",
            birthDate = LocalDate(2012, 1, 1), active = true
        )
        stubResolvedTeamWithNoData()
        coEvery { playerRepository.getPlayersByAgeGroup(ag1) } returns listOf(child)
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)

        val result = useCase(parent.id, teamId)

        assertEquals(1, result.poolSize)
        assertFalse(result.canManageMatch)
        assertFalse(result.canViewMembers)
    }

    @Test
    fun outsiderIsDenied() = runBlocking {
        val outsider = user("p12-outsider")
        stubResolvedTeamWithNoData()
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubIdA) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(outsider.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(outsider.id) } returns emptyList()

        assertFailsWith<ForbiddenException> { useCase(outsider.id, teamId) }
        Unit
    }

    @Test
    fun aNonexistentTeamIsNotFound() = runBlocking {
        // The team lookup happens before the permission check, so no user stubs are needed here.
        coEvery { teamRepository.getTeamById(teamId) } returns null

        assertFailsWith<NotFoundException> { useCase("anyone", teamId) }
        Unit
    }

    @Test
    fun aTeamWhoseClubIsMissingIsNotFound() = runBlocking {
        val admin = user("p2-club-admin")
        stubResolvedTeamWithNoData()
        coEvery { clubRepository.getClubById(clubIdA) } returns null
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN

        assertFailsWith<NotFoundException> { useCase(admin.id, teamId) }
        Unit
    }

    @Test
    fun aTeamWhoseAgeGroupIsMissingIsNotFound() = runBlocking {
        val admin = user("p2-club-admin")
        stubResolvedTeamWithNoData()
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns null
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN

        assertFailsWith<NotFoundException> { useCase(admin.id, teamId) }
        Unit
    }

    @Test
    fun aTeamWhoseSeasonIsMissingIsNotFound() = runBlocking {
        val admin = user("p2-club-admin")
        stubResolvedTeamWithNoData()
        coEvery { seasonRepository.getSeasonById("season-1") } returns null
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN

        assertFailsWith<NotFoundException> { useCase(admin.id, teamId) }
        Unit
    }

    @Test
    fun splitsFixturesIntoUpcomingAndFinishedResults() = runBlocking {
        val admin = user("p2-club-admin")
        val now = Clock.System.now()
        fun match(id: String, offset: kotlin.time.Duration, status: MatchStatus) = Match(
            id = id, clubId = clubIdA, teamId = teamId, opponentId = "opp-1",
            dateTime = now + offset, status = status
        )
        val future = match("m-future", 4.days, MatchStatus.SCHEDULED)
        val won = match("m-won", (-7).days, MatchStatus.FINISHED)
        val lost = match("m-lost", (-14).days, MatchStatus.FINISHED)

        stubResolvedTeamWithNoData()
        coEvery { matchRepository.getMatchesByTeam(teamId) } returns listOf(lost, future, won)
        coEvery { matchEventRepository.getScoreByMatch("m-won") } returns MatchScore("m-won", 3, 1)
        coEvery { matchEventRepository.getScoreByMatch("m-lost") } returns MatchScore("m-lost", 0, 2)
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN

        val result = useCase(admin.id, teamId)

        assertEquals(listOf("m-future"), result.upcomingMatches.map { it.id })
        // Newest finished first.
        assertEquals(listOf("m-won", "m-lost"), result.recentResults.map { it.match.id })
        assertEquals(MatchOutcome.WIN, result.recentResults.first().outcome)
        assertEquals(MatchOutcome.LOSS, result.recentResults.last().outcome)
    }
}
