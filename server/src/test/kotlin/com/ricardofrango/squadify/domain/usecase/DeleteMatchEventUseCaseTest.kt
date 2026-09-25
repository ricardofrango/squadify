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
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchScore
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
        matchEventRepository, matchRepository, liveMatchBus, checkUserPermissionUseCase, clubMatchesBus, transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val eventId = "ev-1"
    private val match = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1))
    private val event = MatchEvent(eventId, matchId, EventType.GOAL, minute = 10, playerId = "p1", createdAt = Instant.fromEpochMilliseconds(1))

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    private fun stubCoachPermission(coach: User) {
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
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
}
