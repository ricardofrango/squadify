package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
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
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Age Group Details' season data: every team's record (sorted by name) and the pool; ratings for staff only. */
class GetAgeGroupSeasonRecordUseCaseTest {

    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val matchRepository = mockk<MatchRepository>()
    private val eventRepository = mockk<MatchEventRepository>()
    private val statsRepository = mockk<MatchPlayerStatsRepository>()
    private val reportRepository = mockk<MatchReportRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetAgeGroupSeasonRecordUseCase(
        ageGroupRepository, teamRepository, seasonRepository, matchRepository, eventRepository, statsRepository,
        reportRepository, opponentRepository, playerRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager
    )

    private val clubId = "club-1"
    private val tomas = Player("p1", clubId, "Tomás", "Bragança", LocalDate(2011, 3, 12), active = true, shirtNumber = 10)

    private fun stubData() {
        coEvery { ageGroupRepository.getAgeGroupById("ag-1") } returns AgeGroup("ag-1", clubId, "Sub-15", "s-1", 2011, 2011)
        coEvery { seasonRepository.getSeasonById("s-1") } returns
            Season("s-1", clubId, "2026/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), active = true)
        coEvery { opponentRepository.getOpponentsByClub(clubId) } returns emptyList()
        coEvery { teamRepository.getTeamsByClub(clubId) } returns listOf(
            Team("t-b", clubId, "ag-1", "s-1", "Sub-15 B", "B", active = true),
            Team("t-a", clubId, "ag-1", "s-1", "Sub-15 A", "A", active = true),
            Team("t-x", clubId, "ag-other", "s-1", "Sub-13 A", "A", active = true)
        )
        coEvery { matchRepository.getMatchesByTeam("t-a") } returns listOf(
            Match("m1", clubId, "t-a", "opp", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.FINISHED, type = MatchType.CHAMPIONSHIP)
        )
        coEvery { matchRepository.getMatchesByTeam("t-b") } returns emptyList()
        coEvery { eventRepository.getScoreByMatch("m1") } returns MatchScore("m1", 2, 0)
        coEvery { statsRepository.getByMatches(listOf("m1")) } returns listOf(MatchPlayerStats("m1", "p1", true, 70, 1, 0, 0, 0, 0, rating = 8))
        coEvery { statsRepository.getByMatches(emptyList()) } returns emptyList()
        coEvery { reportRepository.getCompletedMatchIds(any()) } returns setOf("m1")
        coEvery { playerRepository.getPlayersData(listOf("p1")) } returns listOf(tomas)
        coEvery { playerRepository.getPlayersData(emptyList()) } returns emptyList()
        coEvery { playerRepository.getPlayersByAgeGroup("ag-1") } returns listOf(tomas)
    }

    private fun stubUser(role: AgeGroupRole?, linked: Boolean = false): User {
        val user = User("u", "u@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { userRepository.getUserRoleInClub(user.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(user.id, "ag-1") } returns role
        coEvery { playerRepository.getPlayersByUser(user.id) } returns if (linked) listOf(tomas) else emptyList()
        return user
    }

    @Test
    fun coachGetsEveryTeamOfTheAgeGroupSortedWithRatingsAndThePool() = runBlocking {
        stubData()
        val record = useCase(stubUser(AgeGroupRole.COACH).id, "ag-1")

        assertTrue(record.canSeeRatings)
        assertEquals(listOf("Sub-15 A", "Sub-15 B"), record.teams.map { it.teamName })
        assertEquals(8, record.teams.first().playerLines.single().rating)
        assertEquals(listOf(10), record.pool.map { it.shirtNumber })
    }

    @Test
    fun aLinkedParentGetsNoRatings() = runBlocking {
        stubData()
        val record = useCase(stubUser(role = null, linked = true).id, "ag-1")

        assertFalse(record.canSeeRatings)
        assertTrue(record.teams.flatMap { it.playerLines }.all { it.rating == null })
    }
}
