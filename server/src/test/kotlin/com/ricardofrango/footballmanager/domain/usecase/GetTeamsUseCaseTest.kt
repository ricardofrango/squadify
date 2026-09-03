package com.ricardofrango.footballmanager.domain.usecase

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
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, transactionManager)
    private val useCase = GetTeamsUseCase(teamRepository, checkUserPermissionUseCase, transactionManager)

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
    fun plainMemberWithNoTeamAssignmentCurrentlySeesEveryTeamInTheClub() = runBlocking {
        // Documents today's actual behavior: GetTeamsUseCase never checks TeamStaff at all.
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)

        val result = useCase(member.id, clubIdA)

        assertEquals(listOf(team1, team2, team3), result) // should be scoped down per requirements; isn't
        Unit
    }

    @Test
    fun targetBehaviorMemberShouldOnlySeeTeamsTheyAreAssignedToButCurrentlySeesAll() = runBlocking {
        // TARGET BEHAVIOR (currently fails): a member who coaches Team 1 only should get back [team1],
        // not every team in the club. GetTeamsUseCase does no such filtering today -- it never even
        // calls TeamRepository.getUserRoleInTeam. See "issues to fix.md".
        val coach = user("p5-coach-team1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team1.id) } returns TeamRole.COACH
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team2.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team3.id) } returns null
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)

        val result = useCase(coach.id, clubIdA)

        assertEquals(listOf(team1), result)
        Unit
    }
}
