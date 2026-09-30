package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class DeleteMatchUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val clubMatchesBus = mockk<ClubMatchesBus>(relaxed = true)
    private val useCase = DeleteMatchUseCase(matchRepository, matchEventRepository, checkUserPermissionUseCase, clubMatchesBus, transactionManager)

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val match = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1))
    private val team = Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)

    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    @Test
    fun coachCanDeleteAMatchAndItsCallUpsAndEventsAreClearedFirst() = runBlocking {
        val coach = user("coach")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { matchEventRepository.deleteEventsByMatch(matchId) } returns Unit
        coEvery { matchRepository.manageCallUps(matchId, emptyList()) } returns true
        coEvery { matchRepository.deleteMatch(matchId) } returns true

        val result = useCase(coach.id, matchId)

        assertEquals(true, result)
        // Events and call-ups must be cleared before the match row itself -- no ON DELETE CASCADE.
        coVerifyOrder {
            matchEventRepository.deleteEventsByMatch(matchId)
            matchRepository.manageCallUps(matchId, emptyList())
            matchRepository.deleteMatch(matchId)
        }
        coVerify { clubMatchesBus.publish(clubId, com.ricardofrango.squadify.model.ClubMatchesUpdate(deletedMatchId = matchId)) }
    }

    @Test
    fun ageGroupStaffIsDenied() = runBlocking {
        // Deleting is coach-only, like editing -- deliberately not opened to staff along with the
        // match-day operations (call-ups/start/status).
        val staff = user("staff")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns TeamRole.STAFF
        coEvery { teamRepository.getTeamById(teamId) } returns team

        assertFailsWith<ForbiddenException> { useCase(staff.id, matchId) }
        coVerify(exactly = 0) { matchRepository.deleteMatch(any()) }
        Unit
    }

    private fun stubCoach(): User {
        val coach = user("coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { matchEventRepository.deleteEventsByMatch(matchId) } returns Unit
        coEvery { matchRepository.manageCallUps(matchId, emptyList()) } returns true
        coEvery { matchRepository.deleteMatch(matchId) } returns true
        return coach
    }

    @Test
    fun aFinishedMatchCannotBeDeleted() = runBlocking {
        val coach = stubCoach()
        coEvery { matchRepository.getMatchById(matchId) } returns match.copy(status = MatchStatus.FINISHED)

        assertFailsWith<ConflictException> { useCase(coach.id, matchId) }
        coVerify(exactly = 0) { matchEventRepository.deleteEventsByMatch(any()) }
        coVerify(exactly = 0) { matchRepository.deleteMatch(any()) }
        Unit
    }

    @Test
    fun aMatchThatWasNeverEndedCanStillBeDeleted() = runBlocking {
        // Past its date but never played, cancelled, or started but not ended -- none of these count
        // as "happened", so all stay deletable.
        val coach = stubCoach()
        listOf(MatchStatus.SCHEDULED, MatchStatus.CANCELLED, MatchStatus.LIVE, MatchStatus.HALF_TIME).forEach { status ->
            coEvery { matchRepository.getMatchById(matchId) } returns match.copy(status = status)
            assertEquals(true, useCase(coach.id, matchId), "status $status")
        }
    }

    @Test
    fun matchNotFound() = runBlocking {
        coEvery { matchRepository.getMatchById(matchId) } returns null

        assertFailsWith<NotFoundException> { useCase("someone", matchId) }
        Unit
    }
}
