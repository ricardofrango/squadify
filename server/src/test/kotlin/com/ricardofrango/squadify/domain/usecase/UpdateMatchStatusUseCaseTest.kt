package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

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
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager
    )
    private val notifyMatchEventUseCase = mockk<NotifyMatchEventUseCase>(relaxed = true)
    private val clubMatchesBus = mockk<ClubMatchesBus>(relaxed = true)
    private val useCase = UpdateMatchStatusUseCase(
        matchRepository, matchEventRepository, liveMatchBus, checkUserPermissionUseCase, notifyMatchEventUseCase, clubMatchesBus, transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    private fun stubCoachPermission(coach: User) {
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
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
        coVerify { notifyMatchEventUseCase(matchId, MatchNotificationEvent.SECOND_HALF_STARTED) }
    }

    @Test
    fun liveToHalfTimeNotifiesHalfTime() = runBlocking {
        val coach = coach()
        val liveMatch = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        stubCoachPermission(coach)
        coEvery { matchRepository.updateMatchStatus(matchId, MatchStatus.HALF_TIME) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 1, 0)

        val result = useCase(coach.id, matchId, MatchStatus.HALF_TIME)

        assert(result)
        coVerify { notifyMatchEventUseCase(matchId, MatchNotificationEvent.HALF_TIME) }
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
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any()) }
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
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns TeamRole.STAFF
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)
        coEvery { matchRepository.updateMatchStatus(matchId, MatchStatus.FINISHED) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

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

        val result = useCase(coach.id, matchId, MatchStatus.FINISHED)

        assert(result)
        coVerify { notifyMatchEventUseCase(matchId, MatchNotificationEvent.FULL_TIME) }
        coVerify { clubMatchesBus.publish(clubId, com.ricardofrango.squadify.model.ClubMatchesUpdate(match = liveMatch.copy(status = MatchStatus.FINISHED))) }
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
        coVerify { notifyMatchEventUseCase(matchId, MatchNotificationEvent.CANCELLED) }
    }
}
