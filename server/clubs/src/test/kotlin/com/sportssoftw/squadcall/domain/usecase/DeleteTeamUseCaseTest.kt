package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.ClubAccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.ClubMatchesBus
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.ClubMatchesUpdate
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.Match
import com.sportssoftw.squadcall.model.MatchStatus
import com.sportssoftw.squadcall.model.Team
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class DeleteTeamUseCaseTest {

    private val matchRepository = mockk<MatchRepository>(relaxed = true)
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
    private val useCase = DeleteTeamUseCase(teamRepository, matchRepository, ClubAccessPolicy(checkUserPermissionUseCase), clubMatchesBus, transactionManager)

    private val clubId = "club-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val team = Team(teamId, clubId, ag1, "s-1", "Sub-15 B", "B", active = true)
    private val played = Match("m-1", clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.FINISHED)
    private val upcoming = Match("m-2", clubId, teamId, "opp-2", dateTime = Instant.fromEpochMilliseconds(2))

    private fun user(id: String, clubRole: ClubRole, teamRole: AgeGroupRole? = null): User {
        val user = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(id) } returns user
        coEvery { userRepository.getUserRoleInClub(id, clubId) } returns clubRole
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(id, ag1) } returns teamRole
        return user
    }

    init {
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { teamRepository.deleteTeam(teamId) } returns true
        coEvery { matchRepository.getMatchesByTeam(teamId) } returns listOf(played, upcoming)
    }

    @Test
    fun deletingATeamDeletesAllItsMatchesPlayedOnesTooBeforeTheTeam() = runBlocking {
        val coordinator = user("coord", ClubRole.COORDINATOR)

        assertEquals(2, useCase(coordinator.id, teamId))

        // One delete: the database cascades to the matches and everything under them (V21).
        coVerify(exactly = 1) { teamRepository.deleteTeam(teamId) }
        coVerify { clubMatchesBus.publish(clubId, ClubMatchesUpdate(deletedMatchId = "m-1")) }
        coVerify { clubMatchesBus.publish(clubId, ClubMatchesUpdate(deletedMatchId = "m-2")) }
    }

    @Test
    fun theAgeGroupsCoachCanDeleteATeamByDefault() = runBlocking {
        val coach = user("coach", ClubRole.MEMBER, AgeGroupRole.COACH)
        assertEquals(2, useCase(coach.id, teamId))
    }

    @Test
    fun staffCannotDeleteATeamAndNothingIsTouched() = runBlocking<Unit> {
        val staff = user("staff", ClubRole.MEMBER, AgeGroupRole.STAFF)
        assertFailsWith<ForbiddenException> { useCase(staff.id, teamId) }
        coVerify(exactly = 0) { teamRepository.deleteTeam(any()) }
    }

    @Test
    fun anUnknownTeamIsNotFound() = runBlocking<Unit> {
        coEvery { teamRepository.getTeamById("nope") } returns null
        assertFailsWith<NotFoundException> { useCase("anyone", "nope") }
    }
}
