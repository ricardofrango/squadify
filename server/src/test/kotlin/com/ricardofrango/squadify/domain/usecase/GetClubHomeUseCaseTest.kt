package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
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
    private val useCase = GetClubHomeUseCase(
        clubRepository, teamRepository, playerRepository, seasonRepository, ageGroupRepository,
        matchRepository, opponentRepository, userRepository, checkUserPermissionUseCase, transactionManager
    )

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
        assertTrue(home.alerts.isEmpty())
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
    }
}
