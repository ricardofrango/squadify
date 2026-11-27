package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.ClubAccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.MatchEventRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.service.ClubMatchesBus
import com.sportssoftw.squadcall.domain.service.LiveMatchBus
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ConflictException
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.ClubMatchesUpdate
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.Match
import com.sportssoftw.squadcall.model.MatchScore
import com.sportssoftw.squadcall.model.MatchStatus
import com.sportssoftw.squadcall.model.Team
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class PauseMatchUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val liveMatchBus = mockk<LiveMatchBus>(relaxed = true)
    private val clubMatchesBus = mockk<ClubMatchesBus>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = PauseMatchUseCase(
        matchRepository, matchEventRepository, liveMatchBus, clubMatchesBus, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    private fun stubCoachPermission(coach: User) {
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)
    }

    @Test
    fun onlyThePersonManagingTheGameCanPauseIt() = runBlocking {
        val coach = coach()
        // Another staff member kicked off: the coach has the permission but watches until taking over.
        val managedByOther = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE, liveManagerUserId = "other")
        coEvery { matchRepository.getMatchById(matchId) } returns managedByOther
        stubCoachPermission(coach)

        assertFailsWith<ConflictException> { useCase(coach.id, matchId) }
        coVerify(exactly = 0) { matchRepository.pauseMatch(any(), any()) }
    }

    @Test
    fun pausesALiveMatchAndBroadcastsBothBuses() = runBlocking {
        val coach = coach()
        val liveMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        stubCoachPermission(coach)
        val at = slot<Instant>()
        coEvery { matchRepository.pauseMatch(matchId, capture(at)) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val result = useCase(coach.id, matchId)

        assertEquals(true, result)
        coVerify { clubMatchesBus.publish(clubId, ClubMatchesUpdate(match = liveMatch.copy(pausedAt = at.captured))) }
    }

    @Test
    fun aScheduledMatchCannotBePaused() = runBlocking {
        val coach = coach()
        val scheduledMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.SCHEDULED)
        coEvery { matchRepository.getMatchById(matchId) } returns scheduledMatch
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> { useCase(coach.id, matchId) }
        coVerify(exactly = 0) { matchRepository.pauseMatch(any(), any()) }
        Unit
    }

    @Test
    fun anAlreadyPausedMatchCannotBePausedAgain() = runBlocking {
        val coach = coach()
        val pausedMatch = Match(
            matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1),
            status = MatchStatus.LIVE, pausedAt = Instant.fromEpochMilliseconds(500)
        )
        coEvery { matchRepository.getMatchById(matchId) } returns pausedMatch
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> { useCase(coach.id, matchId) }
        coVerify(exactly = 0) { matchRepository.pauseMatch(any(), any()) }
        Unit
    }

    @Test
    fun plainMemberIsDenied() = runBlocking {
        val member = User("member", "member@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        val liveMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        assertFailsWith<ForbiddenException> { useCase(member.id, matchId) }
        Unit
    }
}
