package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.ClubAccessPolicy
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.ForbiddenException
import com.sportssoftw.squadify.exception.ValidationException
import com.sportssoftw.squadify.model.Club
import com.sportssoftw.squadify.model.ClubStatus
import com.sportssoftw.squadify.model.SportType
import io.mockk.coVerify
import com.sportssoftw.squadify.model.ClubRole
import com.sportssoftw.squadify.model.Team
import com.sportssoftw.squadify.model.AgeGroupRole
import com.sportssoftw.squadify.model.UpdateTeamRequest
import com.sportssoftw.squadify.model.User
import com.sportssoftw.squadify.model.UserStatus
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
 * edit their own team. Fixed by adding `minAgeGroupRole = AgeGroupRole.COACH` alongside the existing club-role
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
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository)
    private val clubRepository = mockk<ClubRepository>()
    private val useCase = UpdateTeamUseCase(teamRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager, clubRepository)

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
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "age-group-1") } returns AgeGroupRole.COACH
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
    fun plainMemberWithNoAgeGroupRoleIsDenied() = runBlocking {
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

    private fun asAdmin(): String {
        val admin = user("p2-club-admin")
        coEvery { teamRepository.getTeamById(team1) } returns team
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { teamRepository.updateTeam(any()) } returns true
        coEvery { clubRepository.getClubById(clubIdA) } returns Club(id = clubIdA, name = "Test FC", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FUTSAL)
        return admin.id
    }

    @Test
    fun theTeamSizeIsSavedWhenTheClubSportAllowsIt() = runBlocking {
        val admin = asAdmin()
        useCase(admin, team1, UpdateTeamRequest(teamSize = 5))
        coVerify { teamRepository.updateTeam(match { it.teamSize == 5 && it.name == "Team 1" }) }
    }

    @Test
    fun aTeamSizeTheSportDoesNotAllowIsRejected() = runBlocking {
        val admin = asAdmin()
        // Futsal plays 4- or 5-a-side.
        assertFailsWith<ValidationException> { useCase(admin, team1, UpdateTeamRequest(teamSize = 11)) }
        Unit
    }

    @Test
    fun aBlankNameIsRejectedAndANameIsTrimmed() = runBlocking {
        val admin = asAdmin()
        assertFailsWith<ValidationException> { useCase(admin, team1, UpdateTeamRequest(name = "   ")) }
        useCase(admin, team1, UpdateTeamRequest(name = "  Sub-15 B "))
        coVerify { teamRepository.updateTeam(match { it.name == "Sub-15 B" }) }
    }
}
