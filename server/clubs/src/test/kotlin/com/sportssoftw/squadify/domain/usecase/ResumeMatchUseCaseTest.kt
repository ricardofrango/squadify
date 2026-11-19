package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.ClubAccessPolicy
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.MatchEventRepository
import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadify.domain.service.ClubMatchesBus
import com.sportssoftw.squadify.domain.service.LiveMatchBus
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.ValidationException
import com.sportssoftw.squadify.model.ClubMatchesUpdate
import com.sportssoftw.squadify.model.ClubRole
import com.sportssoftw.squadify.model.Match
import com.sportssoftw.squadify.model.MatchScore
import com.sportssoftw.squadify.model.MatchStatus
import com.sportssoftw.squadify.model.Team
import com.sportssoftw.squadify.model.AgeGroupRole
import com.sportssoftw.squadify.model.User
import com.sportssoftw.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class ResumeMatchUseCaseTest {

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
    private val useCase = ResumeMatchUseCase(
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
    fun resumingDuringTheFirstHalfShiftsKickoffAtForward() = runBlocking {
        val coach = coach()
        val kickoffAt = Instant.fromEpochMilliseconds(1_000_000)
        val pausedAt = Instant.fromEpochMilliseconds(1_010_000) // paused 10s after kickoff
        val pausedMatch = Match(
            matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1),
            status = MatchStatus.LIVE, kickoffAt = kickoffAt, pausedAt = pausedAt
        )
        coEvery { matchRepository.getMatchById(matchId) } returns pausedMatch
        stubCoachPermission(coach)
        coEvery { matchRepository.resumeMatch(matchId, any(), null) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val update = slot<ClubMatchesUpdate>()
        coEvery { clubMatchesBus.publish(clubId, capture(update)) } returns Unit

        val result = useCase(coach.id, matchId)

        assertEquals(true, result)
        // The exact shifted kickoffAt is now-dependent (pauseDuration = System.currentTimeMillis() - pausedAt),
        // so assert only what's deterministic: secondHalfStartedAt stays null and pausedAt clears.
        val m = update.captured.match
        assertEquals(null, m?.pausedAt)
        assertEquals(null, m?.secondHalfStartedAt)
        assertEquals(true, (m?.kickoffAt ?: kickoffAt) > kickoffAt)
    }

    @Test
    fun resumingDuringTheSecondHalfShiftsSecondHalfStartedAtNotKickoffAt() = runBlocking {
        val coach = coach()
        val kickoffAt = Instant.fromEpochMilliseconds(1_000_000)
        val secondHalfStartedAt = Instant.fromEpochMilliseconds(1_100_000)
        val pausedAt = Instant.fromEpochMilliseconds(1_110_000)
        val pausedMatch = Match(
            matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1),
            status = MatchStatus.LIVE, kickoffAt = kickoffAt, secondHalfStartedAt = secondHalfStartedAt, pausedAt = pausedAt
        )
        coEvery { matchRepository.getMatchById(matchId) } returns pausedMatch
        stubCoachPermission(coach)
        val shiftedSecondHalfStart = slot<Instant>()
        coEvery { matchRepository.resumeMatch(matchId, kickoffAt, capture(shiftedSecondHalfStart)) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val result = useCase(coach.id, matchId)

        assertEquals(true, result)
        coVerify { matchRepository.resumeMatch(matchId, kickoffAt, any()) }
        assertEquals(true, (shiftedSecondHalfStart.captured ?: secondHalfStartedAt) > secondHalfStartedAt)
    }

    @Test
    fun aMatchThatIsNotPausedCannotBeResumed() = runBlocking {
        val coach = coach()
        val liveMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE, pausedAt = null)
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> { useCase(coach.id, matchId) }
        coVerify(exactly = 0) { matchRepository.resumeMatch(any(), any(), any()) }
        Unit
    }

    @Test
    fun aHalfTimeMatchCannotBeResumedThroughThisUseCase() = runBlocking {
        // HALF_TIME -> LIVE is UpdateMatchStatusUseCase's job, not this one's -- pause/resume only
        // ever applies while the match is already LIVE.
        val coach = coach()
        val halfTimeMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.HALF_TIME)
        coEvery { matchRepository.getMatchById(matchId) } returns halfTimeMatch
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> { useCase(coach.id, matchId) }
        Unit
    }
}
