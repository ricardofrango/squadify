package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Covers the "List Club A's teams" rows of `PERMISSION_TEST_MATRIX.md`. GetTeamsUseCase only ever
 * checks `minClubRole = ClubRole.MEMBER` with no teamId, then returns
 * `teamRepository.getTeamsByClub(clubId)` completely unfiltered -- so any club member, regardless of
 * team assignment, currently sees every team in the club. See "issues to fix.md" -- "A plain club
 * member sees the whole club, not just 'the club.'"
 */
class GetTeamsUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, transactionManager)
    private val useCase = GetTeamsUseCase(teamRepository, userRepository, playerRepository, checkUserPermissionUseCase, transactionManager)

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
    private val team3 = team("team-3")

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
    fun clubAdminSeesAllTeams() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)

        val result = useCase(admin.id, clubIdA)

        assertEquals(listOf(team1, team2, team3), result)
        Unit
    }

    @Test
    fun plainMemberWithNoTeamAssignmentSeesNoTeams() = runBlocking {
        // Fixed: a plain member with zero TeamStaff rows now sees an empty list, not the whole club's
        // teams. See "issues to fix.md" -- "A plain club member sees the whole club, not just 'the club.'"
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getUserRoleInTeam(member.id, team1.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(member.id, team2.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(member.id, team3.id) } returns null
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        val result = useCase(member.id, clubIdA)

        assertEquals(emptyList(), result)
        Unit
    }

    @Test
    fun memberOnlySeesTeamsTheyAreAssignedTo() = runBlocking {
        // Fixed: a member who coaches Team 1 only now gets back [team1], not every team in the club.
        // GetTeamsUseCase now filters using TeamRepository.getUserRoleInTeam. See "issues to fix.md".
        val coach = user("p5-coach-team1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team1.id) } returns TeamRole.COACH
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team2.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team3.id) } returns null
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()

        val result = useCase(coach.id, clubIdA)

        assertEquals(listOf(team1), result)
        Unit
    }

    @Test
    fun parentOnlySeesTheTeamTheirChildIsOn() = runBlocking {
        // A parent has no ClubRole and no TeamRole at all -- access flows entirely through
        // TeamPlayers/UserPlayers via CheckUserPermissionUseCase's allowPlayerOrParentAccess path.
        val parent = user("p10-parent")
        val child = com.ricardofrango.footballmanager.model.Player(
            id = "child-1",
            clubId = clubIdA,
            firstName = "Test",
            lastName = "Child",
            birthDate = kotlinx.datetime.LocalDate(2012, 1, 1),
            active = true
        )
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team1.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team2.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team3.id) } returns null
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)
        coEvery { playerRepository.getTeamPlayersByClub(clubIdA) } returns listOf(
            com.ricardofrango.footballmanager.model.TeamPlayer(
                id = "tp-1",
                teamId = team2.id,
                playerId = child.id,
                startDate = kotlinx.datetime.LocalDate(2020, 1, 1),
                status = com.ricardofrango.footballmanager.model.TeamPlayerStatus.ACTIVE
            )
        )

        val result = useCase(parent.id, clubIdA)

        assertEquals(listOf(team2), result)
        Unit
    }
}
