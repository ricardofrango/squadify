package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.ClubAccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.repository.MatchEventRepository
import com.sportssoftw.squadcall.domain.repository.MatchPlayerStatsRepository
import com.sportssoftw.squadcall.domain.repository.MatchReportRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.model.MatchTallies
import com.sportssoftw.squadcall.model.SideTallies
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.Match
import com.sportssoftw.squadcall.model.MatchPlayerStats
import com.sportssoftw.squadcall.model.MatchScore
import com.sportssoftw.squadcall.model.MatchStatus
import com.sportssoftw.squadcall.model.MatchType
import com.sportssoftw.squadcall.model.Opponent
import com.sportssoftw.squadcall.model.Player
import com.sportssoftw.squadcall.model.Season
import com.sportssoftw.squadcall.model.SeasonType
import com.sportssoftw.squadcall.model.Team
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
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

/** Team Details' season record: finished matches only, ratings only for report readers and only once final. */
class GetTeamSeasonRecordUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val matchRepository = mockk<MatchRepository>()
    private val eventRepository = mockk<MatchEventRepository>()
    private val statsRepository = mockk<MatchPlayerStatsRepository>()
    private val reportRepository = mockk<MatchReportRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetTeamSeasonRecordUseCase(
        teamRepository, seasonRepository, matchRepository, eventRepository, statsRepository, reportRepository,
        opponentRepository, playerRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager
    )

    private val clubId = "club-1"
    private val team = Team("t-1", clubId, "ag-1", "s-1", "Sub-15 A", "A", active = true)
    private val tomas = Player("p1", clubId, "Tomás", "Bragança", LocalDate(2011, 3, 12), active = true)

    private fun match(id: String, day: Int, status: MatchStatus, type: MatchType?) = Match(
        id, clubId, team.id, "opp-1", dateTime = Instant.fromEpochMilliseconds(day * 86_400_000L), status = status, type = type
    )

    private fun stubData() {
        coEvery { teamRepository.getTeamById(team.id) } returns team
        coEvery { seasonRepository.getSeasonById("s-1") } returns
            Season("s-1", clubId, "2026/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), active = true)
        coEvery { opponentRepository.getOpponentsByClub(clubId) } returns listOf(Opponent("opp-1", clubId, "Associação Desportiva de Fontelas", "AD Fontelas", null))
        coEvery { matchRepository.getMatchesByTeam(team.id) } returns listOf(
            match("m1", 10, MatchStatus.FINISHED, MatchType.CHAMPIONSHIP),
            match("m2", 20, MatchStatus.FINISHED, MatchType.FRIENDLY),
            match("m3", 30, MatchStatus.SCHEDULED, MatchType.CHAMPIONSHIP)
        )
        coEvery { eventRepository.getScoreByMatch("m1") } returns MatchScore("m1", 3, 1)
        coEvery { eventRepository.getScoreByMatch("m2") } returns MatchScore("m2", 1, 1)
        coEvery { eventRepository.getTalliesByMatches(any()) } answers {
            firstArg<List<String>>().associateWith { MatchTallies(SideTallies(0, 0, 0, 0), SideTallies(null, null, 0, 0), recorded = false) } +
                ("m1" to MatchTallies(SideTallies(6, 3, 5, 7), SideTallies(null, null, 2, 9), recorded = true))
        }
        coEvery { statsRepository.getByMatches(listOf("m2", "m1")) } returns listOf(
            MatchPlayerStats("m1", "p1", true, 70, 2, 0, 0, 0, 0, rating = 9),
            MatchPlayerStats("m2", "p1", true, 70, 0, 1, 0, 0, 0, rating = 6)
        )
        coEvery { reportRepository.getCompletedMatchIds(any()) } returns setOf("m1")
        coEvery { playerRepository.getPlayersData(listOf("p1")) } returns listOf(tomas)
    }

    private fun stubUser(ageGroupRole: AgeGroupRole?, linkedToPool: Boolean = false): User {
        val user = User("u", "u@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { userRepository.getUserRoleInClub(user.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(user.id, "ag-1") } returns ageGroupRole
        coEvery { playerRepository.getPlayersByUser(user.id) } returns if (linkedToPool) listOf(tomas) else emptyList()
        coEvery { playerRepository.getPlayersByAgeGroup("ag-1") } returns listOf(tomas)
        return user
    }

    @Test
    fun coachGetsFinishedMatchesNewestFirstWithFinalRatingsOnly() = runBlocking {
        stubData()
        val coach = stubUser(AgeGroupRole.COACH)

        val record = useCase(coach.id, team.id)

        assertTrue(record.canSeeRatings)
        assertEquals("2026/27", record.seasonName)
        assertEquals(listOf("m2", "m1"), record.matches.map { it.matchId }) // the scheduled one is left out
        assertEquals(3, record.matches.last().ourScore)
        // Each side's corners and fouls, as recorded live.
        assertEquals(listOf(5, 2, 7, 9), record.matches.last().let { listOf(it.cornersFor, it.cornersAgainst, it.foulsFor, it.foulsAgainst) })
        assertEquals(listOf(0, 0, 0, 0), record.matches.first().let { listOf(it.cornersFor, it.cornersAgainst, it.foulsFor, it.foulsAgainst) })
        assertEquals("AD Fontelas", record.matches.first().opponentName)
        assertEquals(9, record.playerLines.first { it.matchId == "m1" }.rating)
        assertNull(record.playerLines.first { it.matchId == "m2" }.rating) // draft report
        assertEquals("Tomás Bragança", record.players.single().name)
    }

    @Test
    fun aLinkedParentGetsTheRecordWithoutRatings() = runBlocking {
        stubData()
        val parent = stubUser(ageGroupRole = null, linkedToPool = true)

        val record = useCase(parent.id, team.id)

        assertFalse(record.canSeeRatings)
        assertEquals(2, record.matches.size)
        assertTrue(record.playerLines.all { it.rating == null })
    }

    @Test
    fun aPlayerNoLongerInThePoolDropsOutOfThePlayerStatsButNotTheResults() = runBlocking {
        stubData()
        val coach = stubUser(AgeGroupRole.COACH)
        // Tomás was removed from the age group after playing both matches.
        coEvery { playerRepository.getPlayersByAgeGroup("ag-1") } returns emptyList()
        coEvery { playerRepository.getPlayersData(emptyList()) } returns emptyList()

        val record = useCase(coach.id, team.id)

        assertTrue(record.playerLines.isEmpty())
        assertTrue(record.players.isEmpty())
        // The team's results stay.
        assertEquals(2, record.matches.size)
        assertEquals(3, record.matches.last().ourScore)
    }
}
