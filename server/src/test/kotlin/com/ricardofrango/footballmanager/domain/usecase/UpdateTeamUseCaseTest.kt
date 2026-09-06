package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.UpdateTeamRequest
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Covers the "write permission" model discussed alongside `PERMISSION_TEST_MATRIX.md`: admin/
 * coordinator can update any team in the club; a team's own coach can update that team (name, etc.);
 * everyone else -- including a coach of a *different* team -- is denied. UpdateTeamUseCase previously
 * required CLUB_ADMIN/COORDINATOR only, with no team-role path at all, which meant a coach could never
 * edit their own team. Fixed by adding `minTeamRole = TeamRole.COACH` alongside the existing club-role
 * check, mirroring the pattern already used by CreateMatchUseCase / AddPlayersToTeamUseCase / etc.
 */
class UpdateTeamUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager)
    private val useCase = UpdateTeamUseCase(teamRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"
    private val team1 = "team-1"
    private val team2 = "team-2"

    private val team = Team(
        id = team1,
        clubId = clubIdA,
        ageGroupId = "age-group-1",
        seasonId = "season-1",
        name = "Team 1",
        code = null,
        active = true
    )

    private fun user(id: String) = User(
        id = id,
        email = "$id@test.com",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        isPlatformAdmin = false,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun clubAdminCanUpdateAnyTeam() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { teamRepository.getTeamById(team1) } returns team
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { teamRepository.updateTeam(any()) } returns true

        useCase(admin.id, team1, UpdateTeamRequest(name = "New Name"))
        Unit
    }

    @Test
    fun coachCanUpdateTheirOwnTeam() = runBlocking {
        val coach = user("p5-coach-team1")
        coEvery { teamRepository.getTeamById(team1) } returns team
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "age-group-1") } returns TeamRole.COACH
        coEvery { teamRepository.updateTeam(any()) } returns true

        useCase(coach.id, team1, UpdateTeamRequest(name = "New Name"))
        Unit
    }

    @Test
    fun coachOfADifferentTeamIsDenied() = runBlocking {
        val otherCoach = user("p6-coach-team2")
        coEvery { teamRepository.getTeamById(team1) } returns team
        coEvery { userRepository.findById(otherCoach.id) } returns otherCoach
        coEvery { userRepository.getUserRoleInClub(otherCoach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(otherCoach.id, "age-group-1") } returns null // coaches team2, not team1

        assertFailsWith<ForbiddenException> {
            useCase(otherCoach.id, team1, UpdateTeamRequest(name = "New Name"))
        }
        Unit
    }

    @Test
    fun plainMemberWithNoTeamRoleIsDenied() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { teamRepository.getTeamById(team1) } returns team
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, "age-group-1") } returns null

        assertFailsWith<ForbiddenException> {
            useCase(member.id, team1, UpdateTeamRequest(name = "New Name"))
        }
        Unit
    }
}
