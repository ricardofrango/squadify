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
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Covers GetPlayersUseCase's two paths per "issues to fix.md" -- "A plain club member sees the whole
 * club, not just 'the club.'": the `teamId`-scoped path (which previously never set `minTeamRole`, the
 * same bug already fixed in GetTeamDetailsUseCase/GetTeamPlayersUseCase/GetTeamStaffUseCase) and the
 * no-filter path (which previously granted every club member the full club roster regardless of team
 * assignment).
 */
class GetPlayersUseCaseTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, transactionManager)
    private val useCase = GetPlayersUseCase(playerRepository, teamRepository, userRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"

    private fun team(id: String) = Team(
        id = id,
        clubId = clubIdA,
        ageGroupId = "age-group-1",
        seasonId = "season-1",
        name = id,
        code = null,
        active = true
    )

    private val team1 = team("team-1")
    private val team2 = team("team-2")

    private fun user(id: String) = User(
        id = id,
        email = "$id@test.com",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        isPlatformAdmin = false,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun player(id: String) = Player(
        id = id,
        clubId = clubIdA,
        firstName = "Test",
        lastName = id,
        birthDate = LocalDate(2012, 1, 1),
        active = true
    )

    @Test
    fun clubAdminSeesEveryPlayerInTheClub() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        val everyone = listOf(player("a"), player("b"))
        coEvery { playerRepository.getPlayersByClub(clubIdA) } returns everyone

        val result = useCase(admin.id, clubIdA)

        assertEquals(everyone, result)
        Unit
    }

    @Test
    fun plainMemberWithNoTeamAssignmentSeesNoPlayers() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2)
        coEvery { teamRepository.getUserRoleInTeam(member.id, team1.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(member.id, team2.id) } returns null
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        val result = useCase(member.id, clubIdA)

        assertEquals(emptyList(), result)
        Unit
    }

    @Test
    fun coachWithNoTeamFilterOnlySeesTheirOwnTeamsPlayers() = runBlocking {
        val coach = user("p5-coach-team1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2)
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team1.id) } returns TeamRole.COACH
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team2.id) } returns null
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        val team1Players = listOf(player("p1"), player("p2"))
        coEvery { playerRepository.getPlayersByTeam(team1.id) } returns team1Players

        val result = useCase(coach.id, clubIdA)

        assertEquals(team1Players, result)
        Unit
    }

    @Test
    fun parentWithNoTeamFilterOnlySeesTheirChildsTeamsPlayers() = runBlocking {
        val parent = user("p10-parent")
        val child = player("child-1")
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2)
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team1.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team2.id) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)
        coEvery { playerRepository.getTeamPlayersByClub(clubIdA) } returns listOf(
            TeamPlayer(id = "tp-1", teamId = team2.id, playerId = child.id, startDate = LocalDate(2020, 1, 1), status = TeamPlayerStatus.ACTIVE)
        )
        val team2Players = listOf(child)
        coEvery { playerRepository.getPlayersByTeam(team2.id) } returns team2Players

        val result = useCase(parent.id, clubIdA)

        assertEquals(team2Players, result)
        Unit
    }

    @Test
    fun coachOfTheRequestedTeamCanSeeItsRosterWhenFilteredByTeamId() = runBlocking {
        val coach = user("p5-coach-team1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team1.id) } returns TeamRole.COACH
        val roster = listOf(player("p1"))
        coEvery { playerRepository.getPlayersByTeam(team1.id) } returns roster

        val result = useCase(coach.id, clubIdA, team1.id)

        assertEquals(roster, result)
        Unit
    }

    @Test
    fun outsiderIsDeniedWhenFilteredByTeamId() = runBlocking {
        val outsider = user("p12-outsider")
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubIdA) } returns null
        coEvery { teamRepository.getUserRoleInTeam(outsider.id, team1.id) } returns null
        coEvery { playerRepository.getPlayersByUser(outsider.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(outsider.id, clubIdA, team1.id)
        }
        Unit
    }
}
