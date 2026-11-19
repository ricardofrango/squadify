package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.ClubAccessPolicy
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.domain.repository.MatchEventRepository
import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.repository.OpponentRepository
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.SeasonRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.ForbiddenException
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.model.Club
import com.sportssoftw.squadify.model.ClubRole
import com.sportssoftw.squadify.model.ClubStatus
import com.sportssoftw.squadify.model.Match
import com.sportssoftw.squadify.model.MatchScore
import com.sportssoftw.squadify.model.MatchStatus
import com.sportssoftw.squadify.model.Opponent
import com.sportssoftw.squadify.model.SportType
import com.sportssoftw.squadify.model.Team
import com.sportssoftw.squadify.model.User
import com.sportssoftw.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class GetOpponentDetailsUseCaseTest {

    private val opponentRepository = mockk<OpponentRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val matchRepository = mockk<MatchRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
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
    private val useCase = GetOpponentDetailsUseCase(
        opponentRepository, clubRepository, matchRepository, matchEventRepository, teamRepository, seasonRepository,
        ClubAccessPolicy(checkUserPermissionUseCase), transactionManager
    )

    private val clubId = "club-1"
    private val opponent = Opponent("o1", clubId, "AD Fontelas", "ADF", stadiumName = "Campo das Fontelas")
    private fun match(id: String, teamId: String, status: MatchStatus, at: Long) =
        Match(id, clubId, teamId, opponent.id, dateTime = Instant.fromEpochMilliseconds(at), status = status)
    private val finished = match("m1", "t1", MatchStatus.FINISHED, 1_000)
    private val live = match("m2", "t2", MatchStatus.LIVE, 2_000)
    private val upcoming = match("m3", "t1", MatchStatus.SCHEDULED, 3_000)

    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    init {
        coEvery { opponentRepository.getOpponentById(opponent.id) } returns opponent
        coEvery { clubRepository.getClubById(clubId) } returns Club(clubId, "CD Ribamar", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)
        coEvery { matchRepository.getMatchesByOpponent(opponent.id) } returns listOf(upcoming, live, finished)
        coEvery { matchEventRepository.getScoresByMatches(listOf("m2", "m1")) } returns mapOf(
            "m2" to MatchScore("m2", 2, 1),
            "m1" to MatchScore("m1", 3, 1)
        )
        coEvery { teamRepository.getTeamsByClub(clubId) } returns listOf(
            Team("t1", clubId, "ag1", "s1", "Sub-15 A", active = true),
            Team("t2", clubId, "ag2", "s1", "Sub-13 A", active = true),
            Team("t3", clubId, "ag3", "s1", "Sub-17 A", active = true)
        )
        coEvery { seasonRepository.getSeasonsByClub(clubId) } returns emptyList()
    }

    private fun asRole(role: ClubRole?): String {
        val u = user("u-$role")
        coEvery { userRepository.findById(u.id) } returns u
        coEvery { userRepository.getUserRoleInClub(u.id, clubId) } returns role
        return u.id
    }

    @Test
    fun aMemberSeesEveryMatchWithTheScoresOfThoseThatStartedButCannotEdit() = runBlocking<Unit> {
        val result = useCase(asRole(ClubRole.MEMBER), opponent.id)

        assertEquals("CD Ribamar", result.clubName)
        assertEquals(listOf("m3", "m2", "m1"), result.matches.map { it.id })
        assertEquals(setOf("m1", "m2"), result.scores.map { it.matchId }.toSet())
        // Only the teams of those matches.
        assertEquals(setOf("t1", "t2"), result.teams.map { it.id }.toSet())
        assertFalse(result.canEdit)
    }

    @Test
    fun aCoordinatorCanEdit() = runBlocking<Unit> {
        assertTrue(useCase(asRole(ClubRole.COORDINATOR), opponent.id).canEdit)
    }

    @Test
    fun someoneOutsideTheClubIsDenied() = runBlocking<Unit> {
        assertFailsWith<ForbiddenException> { useCase(asRole(null), opponent.id) }
    }

    @Test
    fun anUnknownOpponentIsNotFound() = runBlocking<Unit> {
        coEvery { opponentRepository.getOpponentById("nope") } returns null
        assertFailsWith<NotFoundException> { useCase(asRole(ClubRole.MEMBER), "nope") }
    }
}
