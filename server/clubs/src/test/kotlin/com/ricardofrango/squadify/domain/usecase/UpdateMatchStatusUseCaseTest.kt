package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.elapsedMatchSeconds
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

class UpdateMatchStatusUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val liveMatchBus = mockk<LiveMatchBus>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val notifyMatchEventUseCase = mockk<NotifyMatchEventUseCase>(relaxed = true)
    private val clubMatchesBus = mockk<ClubMatchesBus>(relaxed = true)
    private val matchPlayerStatsRepository = mockk<MatchPlayerStatsRepository>(relaxed = true)
    private val useCase = UpdateMatchStatusUseCase(
        matchRepository, matchEventRepository, matchPlayerStatsRepository, liveMatchBus, ClubAccessPolicy(checkUserPermissionUseCase), notifyMatchEventUseCase, clubMatchesBus, transactionManager
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
    fun resumingFromHalfTimeRecordsWhenTheSecondHalfStarted() = runBlocking {
        val coach = coach()
        val halfTimeMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.HALF_TIME)
        coEvery { matchRepository.getMatchById(matchId) } returns halfTimeMatch
        stubCoachPermission(coach)
        coEvery { matchRepository.updateMatchStatus(matchId, MatchStatus.LIVE) } returns true
        coEvery { matchRepository.markSecondHalfStarted(matchId, any()) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val result = useCase(coach.id, matchId, MatchStatus.LIVE)

        assert(result)
        coVerify(exactly = 1) { matchRepository.markSecondHalfStarted(matchId, any()) }
        coVerify { notifyMatchEventUseCase(matchId, MatchNotificationEvent.SECOND_HALF_STARTED, causedBy = any()) }
    }

    @Test
    fun liveToHalfTimeNotifiesHalfTime() = runBlocking {
        val coach = coach()
        // Kicked off 47 minutes ago: a 45-minute half with 2 minutes of stoppage time.
        val liveMatch = Match(
            matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE,
            halfLengthMinutes = 45, kickoffAt = kotlin.time.Clock.System.now() - 47.minutes - 10.seconds
        )
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        stubCoachPermission(coach)
        coEvery { matchRepository.updateMatchStatus(matchId, MatchStatus.HALF_TIME) } returns true
        coEvery { matchRepository.markFirstHalfEnded(matchId, any()) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 1, 0)

        val result = useCase(coach.id, matchId, MatchStatus.HALF_TIME)

        assert(result)
        coVerify { matchRepository.markFirstHalfEnded(matchId, 47) }
        coVerify { notifyMatchEventUseCase(matchId, MatchNotificationEvent.HALF_TIME, causedBy = any()) }
        coVerify(exactly = 0) { matchRepository.markSecondHalfStarted(any(), any()) }
    }

    @Test
    fun kickoffThroughThisUseCaseIsRejected() = runBlocking {
        // SCHEDULED -> LIVE must go through StartMatchUseCase, which also locks in the starting
        // line-up and half length -- allowing it here too would let a caller skip that entirely.
        val coach = coach()
        val scheduledMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.SCHEDULED)
        coEvery { matchRepository.getMatchById(matchId) } returns scheduledMatch
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> {
            useCase(coach.id, matchId, MatchStatus.LIVE)
        }
        coVerify(exactly = 0) { matchRepository.updateMatchStatus(any(), any()) }
        coVerify(exactly = 0) { matchRepository.markSecondHalfStarted(any(), any()) }
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun scheduledToFinishedIsRejected() = runBlocking {
        // The gap this whole transition table closes: nothing previously stopped jumping straight
        // from SCHEDULED to FINISHED, skipping the match entirely.
        val coach = coach()
        val scheduledMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.SCHEDULED)
        coEvery { matchRepository.getMatchById(matchId) } returns scheduledMatch
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> {
            useCase(coach.id, matchId, MatchStatus.FINISHED)
        }
        Unit
    }

    @Test
    fun aTerminalStatusNeverTransitionsAgain() = runBlocking {
        val coach = coach()
        val finishedMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.FINISHED)
        coEvery { matchRepository.getMatchById(matchId) } returns finishedMatch
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> {
            useCase(coach.id, matchId, MatchStatus.LIVE)
        }
        Unit
    }

    @Test
    fun ageGroupStaffCanUpdateMatchStatus() = runBlocking {
        val staff = coach()
        val liveMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns AgeGroupRole.STAFF
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)
        coEvery { matchRepository.updateMatchStatus(matchId, MatchStatus.FINISHED) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)
        stubFullTime()

        val result = useCase(staff.id, matchId, MatchStatus.FINISHED)

        assert(result)
    }

    @Test
    fun goingLiveToFinishedIsAllowed() = runBlocking {
        val coach = coach()
        val liveMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        stubCoachPermission(coach)
        coEvery { matchRepository.updateMatchStatus(matchId, MatchStatus.FINISHED) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)
        stubFullTime()

        val result = useCase(coach.id, matchId, MatchStatus.FINISHED)

        assert(result)
        coVerify { notifyMatchEventUseCase(matchId, MatchNotificationEvent.FULL_TIME, causedBy = any()) }
        coVerify { clubMatchesBus.publish(clubId, com.ricardofrango.squadify.model.ClubMatchesUpdate(match = liveMatch.copy(status = MatchStatus.FINISHED, fullTimeMinute = 0))) }
    }

    @Test
    fun cancellingAScheduledMatchNotifiesTheClub() = runBlocking {
        val coach = coach()
        val scheduledMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.SCHEDULED)
        coEvery { matchRepository.getMatchById(matchId) } returns scheduledMatch
        stubCoachPermission(coach)
        coEvery { matchRepository.updateMatchStatus(matchId, MatchStatus.CANCELLED) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val result = useCase(coach.id, matchId, MatchStatus.CANCELLED)

        assert(result)
        coVerify { notifyMatchEventUseCase(matchId, MatchNotificationEvent.CANCELLED, causedBy = any()) }
    }

    private fun stubFullTime(callUps: List<MatchCallUpWithPlayer> = emptyList(), events: List<MatchEvent> = emptyList()) {
        coEvery { matchRepository.markFullTime(matchId, any()) } returns true
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns callUps
        coEvery { matchEventRepository.getEventsByMatch(matchId) } returns events
    }

    private fun calledUp(playerId: String, starting: Boolean) = MatchCallUpWithPlayer(
        MatchCallUp("cu-$playerId", matchId, playerId, CallUpStatus.CALLED_UP, isStarting = starting),
        Player(playerId, clubId, "F", "L", kotlinx.datetime.LocalDate(2011, 1, 1), active = true)
    )

    @Test
    fun fullTimeRecordsTheMinuteAndStoresEachCalledUpPlayersStats() = runBlocking {
        val coach = coach()
        val now = kotlin.time.Clock.System.now()
        // Second half restarted 48 minutes ago on a 45-minute half: the clock reads 93'.
        val liveMatch = Match(
            matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE,
            halfLengthMinutes = 45, kickoffAt = now - 110.minutes, secondHalfStartedAt = now - 48.minutes - 10.seconds,
            firstHalfEndMinute = 47
        )
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        stubCoachPermission(coach)
        coEvery { matchRepository.updateMatchStatus(matchId, MatchStatus.FINISHED) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 1, 0)
        stubFullTime(
            callUps = listOf(calledUp("p1", starting = true), calledUp("p2", starting = false)),
            events = listOf(MatchEvent("e1", matchId, EventType.GOAL, 60, "p1", createdAt = now - 30.minutes))
        )

        useCase(coach.id, matchId, MatchStatus.FINISHED)

        coVerify { matchRepository.markFullTime(matchId, 93) }
        coVerify {
            matchPlayerStatsRepository.replaceForMatch(matchId, match { rows ->
                val p1 = rows.single { it.playerId == "p1" }
                val p2 = rows.single { it.playerId == "p2" }
                rows.size == 2 && p1.minutesPlayed == 47 + 48 && p1.goals == 1 && p1.started &&
                    p2.minutesPlayed == 0 && !p2.started
            })
        }
    }

    @Test
    fun finishingFromTheBreakUsesTheFirstHalfEndMinute() = runBlocking {
        // No second half was played, so the half-time break must not count as playing time.
        val coach = coach()
        val halfTimeMatch = Match(
            matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.HALF_TIME,
            halfLengthMinutes = 45, kickoffAt = kotlin.time.Clock.System.now() - 70.minutes, firstHalfEndMinute = 46
        )
        coEvery { matchRepository.getMatchById(matchId) } returns halfTimeMatch
        stubCoachPermission(coach)
        coEvery { matchRepository.updateMatchStatus(matchId, MatchStatus.FINISHED) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)
        stubFullTime(callUps = listOf(calledUp("p1", starting = true)))

        useCase(coach.id, matchId, MatchStatus.FINISHED)

        coVerify { matchRepository.markFullTime(matchId, 46) }
        coVerify { matchPlayerStatsRepository.replaceForMatch(matchId, match { it.single().minutesPlayed == 46 }) }
    }

    @Test
    fun onlyFullTimeStoresStats() = runBlocking {
        val coach = coach()
        val scheduledMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.SCHEDULED)
        coEvery { matchRepository.getMatchById(matchId) } returns scheduledMatch
        stubCoachPermission(coach)
        coEvery { matchRepository.updateMatchStatus(matchId, MatchStatus.CANCELLED) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        useCase(coach.id, matchId, MatchStatus.CANCELLED)

        coVerify(exactly = 0) { matchPlayerStatsRepository.replaceForMatch(any(), any()) }
        coVerify(exactly = 0) { matchRepository.markFullTime(any(), any()) }
    }

    @Test
    fun aClockLeftPausedAtTheBreakDoesNotCarryIntoTheSecondHalf() = runBlocking {
        val coach = coach()
        // Paused at 31' and never resumed before half time: the half ends at 31', and the pause ends with it.
        val kickoff = kotlin.time.Clock.System.now() - 40.minutes
        val pausedLive = Match(
            matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE,
            halfLengthMinutes = 30, kickoffAt = kickoff, pausedAt = kickoff + 31.minutes
        )
        coEvery { matchRepository.getMatchById(matchId) } returns pausedLive
        stubCoachPermission(coach)
        coEvery { matchRepository.updateMatchStatus(matchId, any()) } returns true
        coEvery { matchRepository.markFirstHalfEnded(matchId, any()) } returns true
        coEvery { matchRepository.markSecondHalfStarted(matchId, any()) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        useCase(coach.id, matchId, MatchStatus.HALF_TIME)
        coVerify { matchRepository.markFirstHalfEnded(matchId, 31) }
        coVerify { liveMatchBus.publish(matchId, match { it.match?.pausedAt == null && it.match?.firstHalfEndMinute == 31 }) }

        // Even if the stored match still had a pause, starting the second half runs the clock at once from 30:00.
        coEvery { matchRepository.getMatchById(matchId) } returns pausedLive.copy(status = MatchStatus.HALF_TIME, firstHalfEndMinute = 31)
        useCase(coach.id, matchId, MatchStatus.LIVE)
        coVerify {
            liveMatchBus.publish(matchId, match {
                val m = it.match
                m != null && m.pausedAt == null && m.secondHalfStartedAt != null &&
                    elapsedMatchSeconds(m, m.secondHalfStartedAt!!) == 30 * 60
            })
        }
    }

    @Test
    fun aOneHalfMatchHasNoHalfTime() = runBlocking {
        val coach = coach()
        val oneHalf = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE, halves = 1)
        coEvery { matchRepository.getMatchById(matchId) } returns oneHalf
        stubCoachPermission(coach)
        assertFailsWith<ValidationException> { useCase(coach.id, matchId, MatchStatus.HALF_TIME) }
        coVerify(exactly = 0) { matchRepository.updateMatchStatus(any(), any()) }
    }
}
