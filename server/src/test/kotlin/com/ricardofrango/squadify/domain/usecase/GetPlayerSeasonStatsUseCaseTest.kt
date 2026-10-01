package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.Opponent
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Season stats on Player Details: grouped by season, ratings only for report readers and only once final. */
class GetPlayerSeasonStatsUseCaseTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val matchRepository = mockk<MatchRepository>()
    private val statsRepository = mockk<MatchPlayerStatsRepository>()
    private val reportRepository = mockk<MatchReportRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
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
    private val useCase = GetPlayerSeasonStatsUseCase(
        playerRepository, matchRepository, statsRepository, reportRepository, teamRepository, ageGroupRepository,
        seasonRepository, opponentRepository, checkUserPermissionUseCase, transactionManager
    )

    private val clubId = "club-1"
    private val player = Player("p1", clubId, "Tomás", "Bragança", LocalDate(2011, 3, 12), active = true)
    private val oldSeason = Season("s-old", clubId, "2025/26", SeasonType.EUROPEAN_YEAR, LocalDate(2025, 9, 1), LocalDate(2026, 6, 30), active = false)
    private val currentSeason = Season("s-now", clubId, "2026/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), active = true)
    private val oldTeam = Team("t-old", clubId, "ag-old", oldSeason.id, "Sub-13 A", "A", active = true)
    private val team = Team("t-now", clubId, "ag-now", currentSeason.id, "Sub-15 A", "A", active = true)

    private fun match(id: String, teamId: String, day: Int, type: MatchType?, fullTime: Int? = 70) = Match(
        id, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(day * 86_400_000L),
        status = MatchStatus.FINISHED, type = type, fullTimeMinute = fullTime
    )

    private val m1 = match("m1", team.id, 30, MatchType.CHAMPIONSHIP)
    private val m2 = match("m2", team.id, 20, MatchType.FRIENDLY)
    private val mOld = match("m-old", oldTeam.id, 10, null, fullTime = null)

    private fun stubData() {
        coEvery { playerRepository.getPlayerById("p1") } returns player
        coEvery { playerRepository.getAgeGroupIdsByPlayer("p1") } returns listOf("ag-now")
        coEvery { ageGroupRepository.getAgeGroupById("ag-now") } returns AgeGroup("ag-now", clubId, "Sub-15", currentSeason.id, 2011, 2011)
        coEvery { matchRepository.getMatchesByClub(clubId) } returns listOf(m1, m2, mOld)
        coEvery { matchRepository.getCallUpMatchIdsByPlayer("p1") } returns listOf("m1", "m2", "m-old")
        coEvery { teamRepository.getTeamsByClub(clubId) } returns listOf(team, oldTeam)
        coEvery { teamRepository.getTeamById(team.id) } returns team
        coEvery { teamRepository.getTeamById(oldTeam.id) } returns oldTeam
        coEvery { seasonRepository.getSeasonsByClub(clubId) } returns listOf(oldSeason, currentSeason)
        coEvery { opponentRepository.getOpponentsByClub(clubId) } returns listOf(Opponent("opp-1", clubId, "Associação Desportiva de Fontelas", "AD Fontelas", null))
        coEvery { statsRepository.getByPlayer("p1") } returns listOf(
            MatchPlayerStats("m1", "p1", true, 70, 2, 0, 0, 0, 0, rating = 9),
            MatchPlayerStats("m2", "p1", false, 35, 0, 1, 0, 1, 0, rating = 7)
        )
        // m2's report is still a draft: its rating isn't final.
        coEvery { reportRepository.getCompletedMatchIds(any()) } returns setOf("m1")
    }

    private fun stubUser(clubRole: ClubRole, ageGroupRole: TeamRole?): User {
        val user = User("u", "u@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { userRepository.getUserRoleInClub(user.id, clubId) } returns clubRole
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(user.id, any()) } returns ageGroupRole
        coEvery { playerRepository.getPlayersByUser(user.id) } returns emptyList()
        return user
    }

    @Test
    fun coachSeesSeasonsNewestFirstWithOnlyFinalRatings() = runBlocking {
        stubData()
        val coach = stubUser(ClubRole.MEMBER, TeamRole.COACH)

        val result = useCase(coach.id, "p1")

        assertTrue(result.canSeeRatings)
        assertEquals(currentSeason.id, result.currentSeasonId)
        assertEquals(listOf("2026/27", "2025/26"), result.seasons.map { it.seasonName })

        val now = result.seasons.first()
        assertEquals(listOf("Sub-15 A"), now.teamNames)
        assertEquals(listOf("m1", "m2"), now.matches.map { it.matchId })
        assertEquals("AD Fontelas", now.matches.first().opponentName)
        assertEquals(9, now.matches[0].rating)
        assertNull(now.matches[1].rating) // draft report
        assertEquals(0, now.preStatsMatchCount)

        // The older season only has a match from before stats existed.
        val old = result.seasons.last()
        assertTrue(old.matches.isEmpty())
        assertEquals(1, old.preStatsMatchCount)
        assertEquals(listOf("Sub-13 A"), old.teamNames)
    }

    @Test
    fun aParentOrPlayerNeverReceivesRatings() = runBlocking {
        stubData()
        val parent = stubUser(ClubRole.MEMBER, null)

        val result = useCase(parent.id, "p1")

        assertFalse(result.canSeeRatings)
        assertTrue(result.seasons.flatMap { it.matches }.all { it.rating == null })
        assertEquals(2, result.seasons.first().matches.size)
    }

    @Test
    fun aCoachSeesTheStaffLayoutBeforeAnyMatchIsRated() = runBlocking {
        stubData()
        coEvery { statsRepository.getByPlayer("p1") } returns emptyList()
        coEvery { reportRepository.getCompletedMatchIds(any()) } returns emptySet()
        val coach = stubUser(ClubRole.MEMBER, TeamRole.COACH)

        assertTrue(useCase(coach.id, "p1").canSeeRatings)
    }
}
