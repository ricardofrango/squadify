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
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.CreateMatchEventRequest
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Minimal first coverage for this previously-untested use case -- see "Not found" is modelled
 * inconsistently for the same resource" in PROCESS_FLOWS.md §11: the match lookup now throws
 * NotFoundException (was ValidationException) to match every other Match lookup in the codebase.
 */
class AddMatchEventUseCaseTest {

    private val matchEventRepository = mockk<MatchEventRepository>()
    private val matchRepository = mockk<MatchRepository>()
    private val liveMatchBus = mockk<LiveMatchBus>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
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
    private val useCase = AddMatchEventUseCase(
        matchEventRepository, matchRepository, liveMatchBus, checkUserPermissionUseCase, notifyMatchEventUseCase, clubMatchesBus, transactionManager
    )

    @Test
    fun aNonexistentMatchIsNotFound() = runBlocking {
        // The match lookup happens before the permission check, so no user stubs are needed here.
        coEvery { matchRepository.getMatchById("no-such-match") } returns null

        assertFailsWith<NotFoundException> {
            useCase("anyone", "no-such-match", CreateMatchEventRequest(type = EventType.GOAL, minute = 10))
        }
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any()) }
    }

    @Test
    fun aGoalNotifiesTheClub() = runBlocking {
        val match = Match("m-1", "club-1", "t-1", "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
        val coach = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { matchRepository.getMatchById("m-1") } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, "club-1") } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "ag-1") } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById("t-1") } returns Team("t-1", "club-1", "ag-1", "s-1", "Sub-15 A", "A", active = true)
        coEvery { matchEventRepository.addEvent(any()) } answers { firstArg() }
        coEvery { matchEventRepository.getScoreByMatch("m-1") } returns MatchScore("m-1", 1, 0)

        useCase(coach.id, "m-1", CreateMatchEventRequest(type = EventType.GOAL, minute = 23))

        coVerify { notifyMatchEventUseCase("m-1", MatchNotificationEvent.GOAL) }
        coVerify { clubMatchesBus.publish("club-1", ClubMatchesUpdate(score = MatchScore("m-1", 1, 0))) }
    }

    @Test
    fun anOwnGoalAlsoNotifiesTheClub() = runBlocking {
        val match = Match("m-1", "club-1", "t-1", "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
        val coach = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { matchRepository.getMatchById("m-1") } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, "club-1") } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "ag-1") } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById("t-1") } returns Team("t-1", "club-1", "ag-1", "s-1", "Sub-15 A", "A", active = true)
        coEvery { matchEventRepository.addEvent(any()) } answers { firstArg() }
        coEvery { matchEventRepository.getScoreByMatch("m-1") } returns MatchScore("m-1", 0, 1)

        useCase(coach.id, "m-1", CreateMatchEventRequest(type = EventType.OWN_GOAL, minute = 40))

        coVerify { notifyMatchEventUseCase("m-1", MatchNotificationEvent.GOAL) }
    }

    @Test
    fun aNonGoalEventDoesNotNotify() = runBlocking {
        val match = Match("m-1", "club-1", "t-1", "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
        val coach = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { matchRepository.getMatchById("m-1") } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, "club-1") } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "ag-1") } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById("t-1") } returns Team("t-1", "club-1", "ag-1", "s-1", "Sub-15 A", "A", active = true)
        coEvery { matchEventRepository.addEvent(any()) } answers { firstArg() }
        coEvery { matchEventRepository.getScoreByMatch("m-1") } returns MatchScore("m-1", 0, 0)

        useCase(coach.id, "m-1", CreateMatchEventRequest(type = EventType.YELLOW_CARD, minute = 12))

        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any()) }
    }

    @Test
    fun eventsCanOnlyBeLoggedWhileTheMatchIsLive() = runBlocking {
        val coach = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, "club-1") } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "ag-1") } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById("t-1") } returns Team("t-1", "club-1", "ag-1", "s-1", "Sub-15 A", "A", active = true)

        listOf(MatchStatus.SCHEDULED, MatchStatus.FINISHED, MatchStatus.CANCELLED).forEach { status ->
            coEvery { matchRepository.getMatchById("m-1") } returns
                Match("m-1", "club-1", "t-1", "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = status)
            assertFailsWith<ValidationException>("status $status") {
                useCase(coach.id, "m-1", CreateMatchEventRequest(type = EventType.GOAL, minute = 50))
            }
        }
        coVerify(exactly = 0) { matchEventRepository.addEvent(any()) }
    }

    @Test
    fun eachEventRecordsTheHalfItHappenedIn() = runBlocking {
        val coach = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, "club-1") } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "ag-1") } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById("t-1") } returns Team("t-1", "club-1", "ag-1", "s-1", "Sub-15 A", "A", active = true)
        coEvery { matchEventRepository.addEvent(any()) } answers { firstArg() }
        coEvery { matchEventRepository.getScoreByMatch("m-1") } returns MatchScore("m-1", 0, 0)
        val base = Match("m-1", "club-1", "t-1", "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
        val request = CreateMatchEventRequest(type = EventType.YELLOW_CARD, minute = 20)

        coEvery { matchRepository.getMatchById("m-1") } returns base
        assertEquals(1, useCase(coach.id, "m-1", request).period, "first half")

        coEvery { matchRepository.getMatchById("m-1") } returns base.copy(status = MatchStatus.HALF_TIME)
        assertEquals(1, useCase(coach.id, "m-1", request).period, "during the break")

        coEvery { matchRepository.getMatchById("m-1") } returns base.copy(secondHalfStartedAt = Instant.fromEpochMilliseconds(5))
        assertEquals(2, useCase(coach.id, "m-1", request).period, "second half")
    }

    private fun stubLiveMatchAndCoach(): User {
        val coach = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { matchRepository.getMatchById("m-1") } returns Match("m-1", "club-1", "t-1", "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, "club-1") } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "ag-1") } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById("t-1") } returns Team("t-1", "club-1", "ag-1", "s-1", "Sub-15 A", "A", active = true)
        coEvery { matchEventRepository.addEvent(any()) } answers { firstArg() }
        coEvery { matchEventRepository.getScoreByMatch("m-1") } returns MatchScore("m-1", 1, 0)
        return coach
    }

    @Test
    fun aGoalWithAnAssistSavesBothAndLinksTheAssistToTheGoal() = runBlocking {
        val coach = stubLiveMatchAndCoach()
        coEvery { matchRepository.getCallUpByMatchAndPlayer("m-1", "p-assist") } returns
            MatchCallUp("cu", "m-1", "p-assist", CallUpStatus.CALLED_UP)
        val saved = mutableListOf<MatchEvent>()
        coEvery { matchEventRepository.addEvent(capture(saved)) } answers { firstArg() }

        val goal = useCase(coach.id, "m-1", CreateMatchEventRequest(EventType.GOAL, 23, playerId = "p-scorer", assistPlayerId = "p-assist"))

        assertEquals(2, saved.size)
        val assist = saved.single { it.type == EventType.ASSIST }
        assertEquals("p-assist", assist.playerId)
        assertEquals(goal.id, assist.relatedEventId)
        assertEquals(23, assist.minute)
        assertEquals(goal.period, assist.period)
    }

    @Test
    fun aGoalWithoutAnAssistSavesOnlyTheGoal() = runBlocking {
        val coach = stubLiveMatchAndCoach()

        useCase(coach.id, "m-1", CreateMatchEventRequest(EventType.GOAL, 23, playerId = "p-scorer"))

        coVerify(exactly = 1) { matchEventRepository.addEvent(any()) }
    }

    @Test
    fun anAssistIsRejectedWhenItCantBelongToTheGoal() = runBlocking {
        val coach = stubLiveMatchAndCoach()
        coEvery { matchRepository.getCallUpByMatchAndPlayer("m-1", "p-not-called-up") } returns null

        val bad = listOf(
            CreateMatchEventRequest(EventType.GOAL, 23, playerId = "p-scorer", assistPlayerId = "p-scorer"), // own goal assist
            CreateMatchEventRequest(EventType.YELLOW_CARD, 23, playerId = "p-scorer", assistPlayerId = "p-x"), // not a goal
            CreateMatchEventRequest(EventType.GOAL, 23, isOpponent = true, assistPlayerId = "p-x"),           // their goal
            CreateMatchEventRequest(EventType.GOAL, 23, playerId = "p-scorer", assistPlayerId = "p-not-called-up")
        )
        bad.forEach { request -> assertFailsWith<ValidationException>(request.toString()) { useCase(coach.id, "m-1", request) } }
        coVerify(exactly = 0) { matchEventRepository.addEvent(any()) }
    }
}
