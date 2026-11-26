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
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.ClubMatchesUpdate
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.EventType
import com.sportssoftw.squadcall.model.Match
import com.sportssoftw.squadcall.model.MatchStatus
import com.sportssoftw.squadcall.model.MatchEvent
import com.sportssoftw.squadcall.model.MatchScore
import com.sportssoftw.squadcall.model.Team
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class DeleteMatchEventUseCaseTest {

    private val matchEventRepository = mockk<MatchEventRepository>()
    private val matchRepository = mockk<MatchRepository>()
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
    private val clubMatchesBus = mockk<ClubMatchesBus>(relaxed = true)
    private val useCase = DeleteMatchEventUseCase(
        matchEventRepository, matchRepository, liveMatchBus, ClubAccessPolicy(checkUserPermissionUseCase), clubMatchesBus, transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val eventId = "ev-1"
    private val match = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.LIVE)
    private val event = MatchEvent(eventId, matchId, EventType.GOAL, minute = 10, playerId = "p1", createdAt = Instant.fromEpochMilliseconds(1))

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    private fun stubCoachPermission(coach: User) {
        coEvery { matchEventRepository.getEventsByMatch(matchId) } returns listOf(event)
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)
    }

    @Test
    fun coachDeletesAnEventAndScoreIsRebroadcast() = runBlocking {
        val coach = coach()
        coEvery { matchRepository.getMatchById(matchId) } returns match
        stubCoachPermission(coach)
        coEvery { matchEventRepository.getEventById(eventId) } returns event
        coEvery { matchEventRepository.deleteEvent(eventId) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val result = useCase(coach.id, matchId, eventId)

        assertEquals(true, result)
        coVerify { matchEventRepository.deleteEvent(eventId) }
        coVerify { clubMatchesBus.publish(clubId, ClubMatchesUpdate(score = MatchScore(matchId, 0, 0))) }
    }

    @Test
    fun rejectsAnEventThatBelongsToAnotherMatch() = runBlocking {
        val coach = coach()
        coEvery { matchRepository.getMatchById(matchId) } returns match
        stubCoachPermission(coach)
        coEvery { matchEventRepository.getEventById(eventId) } returns event.copy(matchId = "other-match")

        assertFailsWith<ValidationException> {
            useCase(coach.id, matchId, eventId)
        }
        Unit
    }

    @Test
    fun missingEventThrowsNotFound() = runBlocking {
        val coach = coach()
        coEvery { matchRepository.getMatchById(matchId) } returns match
        stubCoachPermission(coach)
        coEvery { matchEventRepository.getEventById(eventId) } returns null

        assertFailsWith<NotFoundException> {
            useCase(coach.id, matchId, eventId)
        }
        Unit
    }

    @Test
    fun plainMemberIsDenied() = runBlocking {
        val member = User("member", "member@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(member.id, matchId, eventId)
        }
        Unit
    }

    @Test
    fun eventsOfAFinishedMatchCannotBeRemoved() = runBlocking {
        val coach = coach()
        coEvery { matchRepository.getMatchById(matchId) } returns match.copy(status = MatchStatus.FINISHED)
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> { useCase(coach.id, matchId, eventId) }
        coVerify(exactly = 0) { matchEventRepository.deleteEvent(any()) }
        Unit
    }

    @Test
    fun deletingAGoalAlsoDeletesItsAssistFirst() = runBlocking {
        val coach = coach()
        coEvery { matchRepository.getMatchById(matchId) } returns match
        stubCoachPermission(coach)
        val assist = MatchEvent("ev-a", matchId, EventType.ASSIST, minute = 10, playerId = "p2", createdAt = Instant.fromEpochMilliseconds(1), relatedEventId = eventId)
        coEvery { matchEventRepository.getEventsByMatch(matchId) } returns listOf(event, assist)
        coEvery { matchEventRepository.getEventById(eventId) } returns event
        coEvery { matchEventRepository.deleteEvent(any()) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        useCase(coach.id, matchId, eventId)

        coVerifyOrder {
            matchEventRepository.deleteEvent("ev-a")
            matchEventRepository.deleteEvent(eventId)
        }
    }
}
