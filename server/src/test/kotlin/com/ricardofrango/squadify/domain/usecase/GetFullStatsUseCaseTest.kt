package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.StoredTrainingSession
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TrainingRating
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** The Full stats data: the age group's, whether entered from a team or from it; ratings and trainings for staff only. */
class GetFullStatsUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val matchRepository = mockk<MatchRepository>()
    private val eventRepository = mockk<MatchEventRepository>()
    private val statsRepository = mockk<MatchPlayerStatsRepository>()
    private val reportRepository = mockk<MatchReportRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val sessionRepository = mockk<TrainingSessionRepository>()
    private val ratingRepository = mockk<TrainingRatingRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    // 24 Oct 2026, noon: the session on the 20th has ended, the one on the 30th hasn't.
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-24T12:00:00Z")
    }
    private val useCase = GetFullStatsUseCase(
        teamRepository, ageGroupRepository, seasonRepository, matchRepository, eventRepository, statsRepository, reportRepository,
        playerRepository, sessionRepository, ratingRepository, checkUserPermissionUseCase, transactionManager, clock
    )

    private val clubId = "club-1"
    private val ageGroup = AgeGroup("ag-1", clubId, "Sub-13", "s-1", 2014, 2014)
    private val teamA = Team("t-a", clubId, "ag-1", "s-1", "Sub-13 A", "A", active = true)
    private val teamB = Team("t-b", clubId, "ag-1", "s-1", "Sub-13 B", "B", active = true)
    private fun player(id: String) = Player(id, clubId, "F$id", "L", LocalDate(2014, 1, 1), active = true)
    private val pool = listOf(player("p1"), player("p2"), player("p3"))

    private fun match(id: String, team: Team, status: MatchStatus = MatchStatus.FINISHED) =
        Match(id, clubId, team.id, "opp", dateTime = Instant.parse("2026-10-18T10:00:00Z"), status = status, type = MatchType.CHAMPIONSHIP)

    private fun session(id: String, day: Int) =
        StoredTrainingSession(TrainingSession(id, "ag-1", LocalDate(2026, 10, day), LocalTime(18, 0), LocalTime(19, 30)), null)

    private fun stub() {
        coEvery { teamRepository.getTeamById(teamA.id) } returns teamA
        coEvery { teamRepository.getTeamsByClub(clubId) } returns listOf(teamB, teamA)
        coEvery { ageGroupRepository.getAgeGroupById("ag-1") } returns ageGroup
        coEvery { seasonRepository.getSeasonById("s-1") } returns
            Season("s-1", clubId, "2026/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), active = true)
        coEvery { matchRepository.getMatchesByTeam(teamA.id) } returns listOf(match("m1", teamA), match("m9", teamA, MatchStatus.SCHEDULED))
        coEvery { matchRepository.getMatchesByTeam(teamB.id) } returns listOf(match("m2", teamB))
        coEvery { eventRepository.getScoreByMatch(any()) } answers { MatchScore(firstArg(), 2, 1) }
        coEvery { statsRepository.getByMatches(any()) } answers {
            val ids = firstArg<List<String>>()
            listOf(
                MatchPlayerStats("m1", "p1", true, 60, 1, 0, 0, 1, 0, rating = 8),
                MatchPlayerStats("m1", "p2", false, 0, 0, 0, 0, 0, 0), // unused sub for A
                MatchPlayerStats("m2", "p2", true, 60, 0, 0, 0, 0, 0, rating = 7),
                MatchPlayerStats("m1", "gone", true, 60, 0, 0, 0, 0, 0) // no longer in the pool
            ).filter { it.matchId in ids }
        }
        coEvery { reportRepository.getCompletedMatchIds(any()) } returns setOf("m1")
        coEvery { playerRepository.getPlayersByAgeGroup("ag-1") } returns pool
        coEvery { sessionRepository.getByAgeGroup("ag-1") } returns listOf(session("tr1", 20), session("tr2", 30))
        coEvery { ratingRepository.getForSessions(listOf("tr1")) } returns mapOf("tr1" to listOf(TrainingRating("p1", 7.5), TrainingRating("p3", 6.0)))
    }

    private fun user(ageGroupRole: AgeGroupRole?, linkedToPool: Boolean = false): String {
        val user = User("u", "u@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { userRepository.getUserRoleInClub(user.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(user.id, "ag-1") } returns ageGroupRole
        coEvery { playerRepository.getPlayersByUser(user.id) } returns if (linkedToPool) listOf(pool.first()) else emptyList()
        return user.id
    }

    @Test
    fun aTeamGivesItsAgeGroupsDataWithEveryTeamAndFinishedMatch() = runBlocking {
        stub()
        val data = useCase.team(user(AgeGroupRole.COACH), teamA.id)

        // The same as the age group's, so the team filter offers every team; the app preselects A.
        assertEquals("Sub-13", data.title)
        assertEquals(listOf("Sub-13 A", "Sub-13 B"), data.teams.map { it.name })
        assertEquals(setOf("m1", "m2"), data.matches.map { it.matchId }.toSet())
        assertEquals(listOf("p1", "p2", "p3"), data.players.map { it.id })
        // "gone" left the pool.
        assertEquals(setOf("p1", "p2"), data.lines.map { it.playerId }.toSet())
        assertEquals(1, data.lines.first { it.playerId == "p1" }.yellowCards)
        assertEquals(8, data.lines.first { it.playerId == "p1" }.rating)
        // Only the ended session, with its ratings.
        assertEquals(listOf("tr1"), data.trainings.map { it.sessionId })
        assertEquals(2, data.trainings.single().ratings.size)
    }

    @Test
    fun anAgeGroupListsItsWholePoolAndEveryTeam() = runBlocking {
        stub()
        val data = useCase.ageGroup(user(AgeGroupRole.STAFF), "ag-1")

        assertEquals(listOf("Sub-13 A", "Sub-13 B"), data.teams.map { it.name })
        assertEquals(setOf("m1", "m2"), data.matches.map { it.matchId }.toSet())
        assertEquals(listOf("p1", "p2", "p3"), data.players.map { it.id })
        // m2's report isn't completed: its rating isn't final yet.
        assertNull(data.lines.first { it.matchId == "m2" }.rating)
    }

    @Test
    fun aParentGetsTheTableWithoutRatingsOrTrainings() = runBlocking {
        stub()
        val data = useCase.team(user(null, linkedToPool = true), teamA.id)

        assertFalse(data.canSeeRatings)
        assertTrue(data.trainings.isEmpty())
        assertTrue(data.lines.all { it.rating == null })
        assertEquals(1, data.lines.first { it.playerId == "p1" }.goals)
    }
}
