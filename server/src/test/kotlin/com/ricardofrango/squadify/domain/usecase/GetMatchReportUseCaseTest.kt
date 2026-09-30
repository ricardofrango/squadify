package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchReportRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Reading the report: coordinators and age-group staff only (never players/parents), finished matches only. */
class GetMatchReportUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchReportRepository = mockk<MatchReportRepository>()
    private val matchPlayerStatsRepository = mockk<MatchPlayerStatsRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
    init { coEvery { opponentRepository.getOpponentById("opp-1") } returns Opponent("opp-1", "club-1", "Associação Desportiva de Fontelas", "AD Fontelas", null) }
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetMatchReportUseCase(
        matchRepository, matchReportRepository, matchPlayerStatsRepository, userRepository, teamRepository, opponentRepository,
        checkUserPermissionUseCase, transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val finished = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.FINISHED)
    private val player = Player("p1", clubId, "Ana", "Costa", LocalDate(2011, 1, 1), active = true)

    private fun stubRole(role: TeamRole?): User {
        val user = User("u-${role?.name}", "u@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { userRepository.getUserRoleInClub(user.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(user.id, ag1) } returns role
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)
        return user
    }

    private fun stubData() {
        coEvery { matchRepository.getMatchById(matchId) } returns finished
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns
            listOf(MatchCallUpWithPlayer(MatchCallUp("cu", matchId, "p1", CallUpStatus.CALLED_UP, isStarting = true), player))
        coEvery { matchPlayerStatsRepository.getByMatch(matchId) } returns
            listOf(MatchPlayerStats(matchId, "p1", true, 90, 1, 0, 0, 0, 0, rating = 7))
        coEvery { matchReportRepository.getByMatch(matchId) } returns null
    }

    @Test
    fun aCoachReadsTheStatsWithPlayersAndCanEdit() = runBlocking {
        val coach = stubRole(TeamRole.COACH)
        stubData()

        val details = useCase(coach.id, matchId)

        assertNull(details.report)
        assertEquals("Ana", details.playerStats.single().player.firstName)
        assertEquals(7, details.playerStats.single().stats.rating)
        assertTrue(details.canEdit)
        assertEquals("Sub-15 A", details.teamName)
        assertEquals("AD Fontelas", details.opponentName)
    }

    @Test
    fun ageGroupStaffCanReadButNotEditByDefault() = runBlocking {
        val staff = stubRole(TeamRole.STAFF)
        stubData()

        assertFalse(useCase(staff.id, matchId).canEdit)
    }

    @Test
    fun aPlainMemberOrParentCannotReadIt() = runBlocking {
        val member = stubRole(null)
        stubData()

        assertFailsWith<ForbiddenException> { useCase(member.id, matchId) }
        Unit
    }

    @Test
    fun anUnfinishedMatchHasNoReport() = runBlocking {
        val coach = stubRole(TeamRole.COACH)
        coEvery { matchRepository.getMatchById(matchId) } returns finished.copy(status = MatchStatus.LIVE)

        assertFailsWith<ValidationException> { useCase(coach.id, matchId) }
        Unit
    }
}
