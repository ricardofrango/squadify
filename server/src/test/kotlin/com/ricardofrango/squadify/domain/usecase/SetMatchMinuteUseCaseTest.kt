package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import com.ricardofrango.squadify.model.elapsedMatchSeconds
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SetMatchMinuteUseCaseTest {

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
    private val useCase = SetMatchMinuteUseCase(
        matchRepository, matchEventRepository, liveMatchBus, clubMatchesBus, checkUserPermissionUseCase, transactionManager
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
    fun settingTheMinuteDuringTheFirstHalfShiftsKickoffAtSoTheFormulaMatches() = runBlocking {
        val coach = coach()
        val liveMatch = Match(
            matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1),
            status = MatchStatus.LIVE, kickoffAt = Clock.System.now() - 3.minutes
        )
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        stubCoachPermission(coach)
        val kickoffAtSlot = slot<Instant>()
        coEvery { matchRepository.adjustMatchClock(matchId, capture(kickoffAtSlot), null) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val result = useCase(coach.id, matchId, 25)

        assertEquals(true, result)
        val adjustedMatch = liveMatch.copy(kickoffAt = kickoffAtSlot.captured)
        val elapsedMinute = elapsedMatchSeconds(adjustedMatch, Clock.System.now()) / 60
        assertEquals(25, elapsedMinute)
    }

    @Test
    fun settingTheMinuteDuringTheSecondHalfShiftsSecondHalfStartedAtNotKickoffAt() = runBlocking {
        val coach = coach()
        val kickoffAt = Clock.System.now() - 50.minutes
        val secondHalfStartedAt = Clock.System.now() - 5.minutes
        val liveMatch = Match(
            matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1),
            status = MatchStatus.LIVE, kickoffAt = kickoffAt, secondHalfStartedAt = secondHalfStartedAt,
            halfLengthMinutes = 30
        )
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        stubCoachPermission(coach)
        val secondHalfSlot = slot<Instant>()
        coEvery { matchRepository.adjustMatchClock(matchId, kickoffAt, capture(secondHalfSlot)) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val result = useCase(coach.id, matchId, 40)

        assertEquals(true, result)
        val adjustedMatch = liveMatch.copy(secondHalfStartedAt = secondHalfSlot.captured)
        val elapsedMinute = elapsedMatchSeconds(adjustedMatch, Clock.System.now()) / 60
        assertEquals(40, elapsedMinute)
    }

    @Test
    fun settingTheMinuteWhilePausedUsesTheFrozenInstantNotRealNow() = runBlocking {
        val coach = coach()
        val kickoffAt = Clock.System.now() - 20.minutes
        val pausedAt = Clock.System.now() - 5.minutes // paused 5 min ago, frozen at 15'
        val pausedMatch = Match(
            matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1),
            status = MatchStatus.LIVE, kickoffAt = kickoffAt, pausedAt = pausedAt
        )
        coEvery { matchRepository.getMatchById(matchId) } returns pausedMatch
        stubCoachPermission(coach)
        val kickoffAtSlot = slot<Instant>()
        coEvery { matchRepository.adjustMatchClock(matchId, capture(kickoffAtSlot), null) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val result = useCase(coach.id, matchId, 10)

        assertEquals(true, result)
        // Frozen display (still paused) must read 10' -- evaluated at pausedAt, not real now.
        val adjustedMatch = pausedMatch.copy(kickoffAt = kickoffAtSlot.captured)
        val elapsedMinute = elapsedMatchSeconds(adjustedMatch, Clock.System.now()) / 60
        assertEquals(10, elapsedMinute)
    }

    @Test
    fun aScheduledMatchCannotHaveItsMinuteSet() = runBlocking {
        val coach = coach()
        val scheduledMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.SCHEDULED)
        coEvery { matchRepository.getMatchById(matchId) } returns scheduledMatch
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> { useCase(coach.id, matchId, 10) }
        Unit
    }

    @Test
    fun aNegativeMinuteIsRejected() = runBlocking {
        val coach = coach()
        val liveMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE, kickoffAt = Clock.System.now())
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> { useCase(coach.id, matchId, -1) }
        Unit
    }
}
