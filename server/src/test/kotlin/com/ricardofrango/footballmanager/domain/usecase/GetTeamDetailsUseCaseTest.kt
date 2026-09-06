package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * A team's details/roster/staff are now age-group-scoped: the permission check resolves the team to
 * its age group, and staff comes from `AgeGroupStaff`. A coach/staff on that age group, an
 * admin/coordinator, or a parent of a player in the age group's pool can view it.
 */
class GetTeamDetailsUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager
    )
    private val useCase = GetTeamDetailsUseCase(
        teamRepository, ageGroupRepository, checkUserPermissionUseCase, transactionManager
    )

    private val clubIdA = "club-a"
    private val teamId = "team-1"
    private val ag1 = "age-group-1"

    private val team = Team(
        id = teamId, clubId = clubIdA, ageGroupId = ag1, seasonId = "season-1",
        name = "Team 1", code = null, active = true
    )

    private fun user(id: String) = User(
        id = id, email = "$id@test.com", firstName = "Test", lastName = "User",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun coachOfTheAgeGroupCanSeeTeamDetails() = runBlocking {
        val coach = user("p5-coach-ag1")
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
        coEvery { ageGroupRepository.getAgeGroupStaff(ag1) } returns emptyList()

        useCase(coach.id, teamId)
        Unit
    }

    @Test
    fun clubAdminCanViewAnyTeamsDetails() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupStaff(ag1) } returns emptyList()

        useCase(admin.id, teamId)
        Unit
    }

    @Test
    fun outsiderIsDeniedTeamDetails() = runBlocking {
        val outsider = user("p12-outsider")
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubIdA) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(outsider.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(outsider.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(outsider.id, teamId)
        }
        Unit
    }

    @Test
    fun parentOfAPlayerInTheAgeGroupCanSeeTeamDetails() = runBlocking {
        val parent = user("p10-parent")
        val child = Player(
            id = "child-1", clubId = clubIdA, firstName = "Test", lastName = "Child",
            birthDate = LocalDate(2012, 1, 1), active = true
        )
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)
        coEvery { playerRepository.getPlayersByAgeGroup(ag1) } returns listOf(child)
        coEvery { ageGroupRepository.getAgeGroupStaff(ag1) } returns emptyList()

        useCase(parent.id, teamId)
        Unit
    }
}
