package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamPlayer
import com.ricardofrango.footballmanager.model.TeamPlayerStatus
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
 * Exercises the "new finding" from `PERMISSION_TEST_MATRIX.md`: GetTeamDetailsUseCase calls
 * CheckUserPermissionUseCase with `minClubRole = ClubRole.MEMBER` and a `teamId`, but never passes
 * `minTeamRole`. Since CheckUserPermissionUseCase's team-role branch only runs when
 * `teamId != null && minTeamRole != null`, it never fires here -- so even a coach/staff member who
 * genuinely belongs to the team is denied, and only club admin/coordinator can pass.
 *
 * Uses a *real* CheckUserPermissionUseCase (wired to mocked UserRepository/TeamRepository) rather
 * than mocking it directly, so the test exercises the actual end-to-end bug rather than assuming it.
 * See "issues to fix.md" -- "Team-scoped read use cases never set minTeamRole...".
 */
class GetTeamDetailsUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, transactionManager)
    private val useCase = GetTeamDetailsUseCase(teamRepository, playerRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"
    private val teamId = "team-1"

    private val team = Team(
        id = teamId,
        clubId = clubIdA,
        ageGroupId = "age-group-1",
        seasonId = "season-1",
        name = "Team 1",
        code = null,
        active = true
    )

    private fun user(id: String, status: UserStatus = UserStatus.ACTIVE) = User(
        id = id,
        email = "$id@test.com",
        firstName = "Test",
        lastName = "User",
        status = status,
        isPlatformAdmin = false,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun coachOfTheTeamCanSeeItsDetails() = runBlocking {
        // Fixed: GetTeamDetailsUseCase now passes minTeamRole = TeamRole.STAFF, so a coach (or staff
        // member) genuinely assigned to this team can view its own details/roster/staff.
        val coach = user("p5-coach-team1")
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getUserRoleInTeam(coach.id, teamId) } returns TeamRole.COACH
        coEvery { playerRepository.getPlayersByTeam(teamId) } returns emptyList()
        coEvery { teamRepository.getTeamStaff(teamId) } returns emptyList()

        useCase(coach.id, teamId)
        Unit
    }

    @Test
    fun clubAdminCanAlreadyViewAnyTeamsDetails() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { playerRepository.getPlayersByTeam(teamId) } returns emptyList()
        coEvery { teamRepository.getTeamStaff(teamId) } returns emptyList()

        useCase(admin.id, teamId)
        Unit
    }

    @Test
    fun outsiderIsDeniedTeamDetails() = runBlocking {
        val outsider = user("p12-outsider")
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubIdA) } returns null
        coEvery { teamRepository.getUserRoleInTeam(outsider.id, teamId) } returns null
        coEvery { playerRepository.getPlayersByUser(outsider.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(outsider.id, teamId)
        }
        Unit
    }

    @Test
    fun parentOfAPlayerOnTheTeamCanSeeItsDetails() = runBlocking {
        // The player/parent read path: a parent with no club role and no TeamStaff row, but linked
        // via UserPlayers/TeamPlayers to a player on this team, can now view the team's details.
        val parent = user("p10-parent")
        val child = Player(
            id = "child-1",
            clubId = clubIdA,
            firstName = "Test",
            lastName = "Child",
            birthDate = LocalDate(2012, 1, 1),
            active = true
        )
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, teamId) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)
        coEvery { playerRepository.getTeamPlayersByClub(clubIdA) } returns listOf(
            TeamPlayer(
                id = "tp-1",
                teamId = teamId,
                playerId = child.id,
                startDate = LocalDate(2020, 1, 1),
                status = TeamPlayerStatus.ACTIVE
            )
        )
        coEvery { playerRepository.getPlayersByTeam(teamId) } returns emptyList()
        coEvery { teamRepository.getTeamStaff(teamId) } returns emptyList()

        useCase(parent.id, teamId)
        Unit
    }
}
