package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Player
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
 * Rewritten against the current permission model. The previous version of this file referenced a
 * since-removed `Role` enum, a `TeamRepository.isUserStaffOfTeam(...): Boolean` method, and a 2-arg
 * constructor with a non-`Unit` return -- none of which exist anymore, so it no longer compiled. See
 * "issues to fix.md" ("CheckUserPermissionUseCaseTest.kt is stale and doesn't compile").
 *
 * Personas (P1-P13) follow `PERMISSION_TEST_MATRIX.md` at the repo root. Tests prefixed
 * `targetBehavior...` intentionally assert what the permission model *should* do per the business
 * requirements rather than what it does today. They are expected to fail (red) until the matching
 * item in "issues to fix.md" is fixed, and should then flip to green.
 */
class CheckUserPermissionUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, transactionManager)

    private val clubIdA = "club-a"
    private val clubIdB = "club-b"
    private val team1 = "team-1" // Club A
    private val team2 = "team-2" // Club A
    private val team3 = "team-3" // Club A - not assigned to any persona below
    private val team5 = "team-5" // Club B

    private fun user(id: String, isPlatformAdmin: Boolean = false, status: UserStatus = UserStatus.ACTIVE) = User(
        id = id,
        email = "$id@test.com",
        firstName = "Test",
        lastName = "User",
        status = status,
        isPlatformAdmin = isPlatformAdmin,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun player(id: String, clubId: String) = Player(
        id = id,
        clubId = clubId,
        firstName = "Test",
        lastName = "Player",
        birthDate = LocalDate(2012, 1, 1),
        active = true
    )

    private fun teamPlayer(id: String, teamId: String, playerId: String) = TeamPlayer(
        id = id,
        teamId = teamId,
        playerId = playerId,
        startDate = LocalDate(2020, 1, 1),
        status = TeamPlayerStatus.ACTIVE
    )

    // --- P1: Super Admin ---

    @Test
    fun platformAdminBypassesEveryCheckEvenWithoutClubRole() = runBlocking {
        val admin = user("p1-super-admin", isPlatformAdmin = true)
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns null

        useCase(admin.id, clubIdA, minClubRole = ClubRole.CLUB_ADMIN)
        // no exception thrown = pass
        Unit
    }

    // --- Unknown user ---

    @Test
    fun unknownUserIsDenied() = runBlocking {
        coEvery { userRepository.findById("ghost") } returns null

        assertFailsWith<ForbiddenException> {
            useCase("ghost", clubIdA, minClubRole = ClubRole.MEMBER)
        }
        Unit
    }

    // --- P2: Club Admin / P3: Coordinator ---

    @Test
    fun clubAdminPassesClubAdminRequirement() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN

        useCase(admin.id, clubIdA, minClubRole = ClubRole.CLUB_ADMIN)
        Unit
    }

    @Test
    fun coordinatorPassesCoordinatorRequirement() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR

        useCase(coordinator.id, clubIdA, minClubRole = ClubRole.COORDINATOR)
        Unit
    }

    @Test
    fun coordinatorIsDeniedForClubAdminOnlyRequirementUnderCurrentLevelComparison() = runBlocking {
        // Not itself a bug in CheckUserPermissionUseCase: the level comparison it performs
        // (userClubRole.level >= minClubRole.level) is generic and correct as written. The actual bug
        // -- admin and coordinator should be fully equivalent until billing exists -- is that
        // UpdateClubUseCase / UpdateClubMemberRoleUseCase pass `minClubRole = CLUB_ADMIN` instead of
        // COORDINATOR. That fix belongs at those call sites, not in this function. See "issues to fix.md".
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR

        assertFailsWith<ForbiddenException> {
            useCase(coordinator.id, clubIdA, minClubRole = ClubRole.CLUB_ADMIN)
        }
        Unit
    }

    // --- P4: Plain member, no team assignment ---

    @Test
    fun plainMemberWithNoTeamIdPassesMemberRequirement() = runBlocking {
        // This single code path (minClubRole = MEMBER, teamId = null) is used identically by two very
        // different calls, and CheckUserPermissionUseCase has no way to tell them apart:
        //  - GetClubDetailsUseCase relies on it to let any club member view the club profile -- this
        //    is correct, it IS "sees only the club" per the requirements.
        //  - GetTeamsUseCase / GetPlayersUseCase / GetSeasonsUseCase etc. rely on the exact same branch
        //    to list every team/player/season in the club -- this is the documented bug: a plain member
        //    with zero TeamStaff rows should not be able to list club-wide resources, but currently can.
        // Fixing the second case needs those *calling* use cases to stop relying on this shortcut (e.g.
        // scope GetTeamsUseCase to the caller's own TeamStaff rows), not a change to this function.
        // See "issues to fix.md" -- "A plain club member sees the whole club, not just 'the club.'"
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER

        useCase(member.id, clubIdA, minClubRole = ClubRole.MEMBER)
        Unit
    }

    // --- P5 / P6: single-team coach & staff ---

    @Test
    fun coachPassesWhenCallerSuppliesMinTeamRole() = runBlocking {
        // The "already correct" path (e.g. AddMatchEventUseCase, ManageCallUpsUseCase): proves
        // CheckUserPermissionUseCase works fine when a caller passes minTeamRole. The bug in
        // GetTeamDetailsUseCase / GetTeamPlayersUseCase / GetTeamStaffUseCase / GetMatchesUseCase is
        // that they omit it -- see GetTeamDetailsUseCaseTest and "issues to fix.md".
        val coach = user("p5-coach-team1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team1) } returns TeamRole.COACH

        useCase(coach.id, clubIdA, minClubRole = ClubRole.MEMBER, minTeamRole = TeamRole.COACH, teamId = team1)
        Unit
    }

    @Test
    fun staffIsDeniedForATeamTheyDontBelongToEvenWithMinTeamRoleSupplied() = runBlocking {
        val staff = user("p6-staff-team2")
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getUserRoleInTeam(staff.id, team1) } returns null // not on team 1

        assertFailsWith<ForbiddenException> {
            useCase(staff.id, clubIdA, minClubRole = ClubRole.MEMBER, minTeamRole = TeamRole.STAFF, teamId = team1)
        }
        Unit
    }

    // --- P7: multi-role member across two teams in the same club ---

    @Test
    fun multiRoleMemberPassesForEachAssignedTeamOnly() = runBlocking {
        val member = user("p7-multi-role")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getUserRoleInTeam(member.id, team1) } returns TeamRole.COACH
        coEvery { teamRepository.getUserRoleInTeam(member.id, team2) } returns TeamRole.STAFF
        coEvery { teamRepository.getUserRoleInTeam(member.id, team3) } returns null

        useCase(member.id, clubIdA, minClubRole = ClubRole.MEMBER, minTeamRole = TeamRole.COACH, teamId = team1)
        useCase(member.id, clubIdA, minClubRole = ClubRole.MEMBER, minTeamRole = TeamRole.STAFF, teamId = team2)
        assertFailsWith<ForbiddenException> {
            useCase(member.id, clubIdA, minClubRole = ClubRole.MEMBER, minTeamRole = TeamRole.STAFF, teamId = team3)
        }
        Unit
    }

    // --- P8: coach in two different teams in two different clubs ---

    @Test
    fun crossClubCoachPassesIndependentlyInBothClubs() = runBlocking {
        val coach = user("p8-cross-club-coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdB) } returns ClubRole.MEMBER
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team1) } returns TeamRole.COACH
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team5) } returns TeamRole.COACH
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team3) } returns null

        useCase(coach.id, clubIdA, minClubRole = ClubRole.MEMBER, minTeamRole = TeamRole.COACH, teamId = team1)
        useCase(coach.id, clubIdB, minClubRole = ClubRole.MEMBER, minTeamRole = TeamRole.COACH, teamId = team5)
        assertFailsWith<ForbiddenException> {
            useCase(coach.id, clubIdA, minClubRole = ClubRole.MEMBER, minTeamRole = TeamRole.COACH, teamId = team3)
        }
        Unit
    }

    // --- P10: parent linked via UserPlayers/TeamPlayers ---

    @Test
    fun parentIsGrantedAccessToChildsClubAndTeamWhenReadPathOptsIn() = runBlocking {
        // Fixed: CheckUserPermissionUseCase now has an opt-in `allowPlayerOrParentAccess` branch that
        // consults PlayerRepository (UserPlayers via getPlayersByUser, TeamPlayers via
        // getTeamPlayersByClub) when club/team role checks fail. A read use case that passes
        // allowPlayerOrParentAccess = true now grants a parent/player linked to this club and team.
        // See "issues to fix.md" -- "Players and family members have no authorization path at all."
        val parent = user("p10-parent-of-one-child")
        val child = player("child-1", clubIdA)
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team1) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)
        coEvery { playerRepository.getTeamPlayersByClub(clubIdA) } returns listOf(teamPlayer("tp-1", team1, child.id))

        useCase(
            parent.id,
            clubIdA,
            minClubRole = ClubRole.MEMBER,
            teamId = team1,
            allowPlayerOrParentAccess = true
        )
        Unit
    }

    @Test
    fun parentLinkIsIgnoredWhenCallerDoesNotOptIn() = runBlocking {
        // DESIGN INVARIANT: allowPlayerOrParentAccess defaults to false, so write/mutation use cases
        // (which never pass it) remain safe even for a fully-linked parent/player.
        val parent = user("p10-parent-of-one-child")
        val child = player("child-1", clubIdA)
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team1) } returns null

        assertFailsWith<ForbiddenException> {
            useCase(parent.id, clubIdA, minClubRole = ClubRole.MEMBER, teamId = team1)
        }
        Unit
    }

    @Test
    fun parentLinkedToADifferentClubIsDeniedEvenWithOptIn() = runBlocking {
        val parent = user("p10-parent-other-club")
        val child = player("child-2", clubIdB)
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team1) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)

        assertFailsWith<ForbiddenException> {
            useCase(
                parent.id,
                clubIdA,
                minClubRole = ClubRole.MEMBER,
                teamId = team1,
                allowPlayerOrParentAccess = true
            )
        }
        Unit
    }

    @Test
    fun parentLinkedToTheRightClubButADifferentTeamIsDenied() = runBlocking {
        val parent = user("p10-parent-different-team")
        val child = player("child-3", clubIdA)
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team1) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)
        coEvery { playerRepository.getTeamPlayersByClub(clubIdA) } returns listOf(teamPlayer("tp-2", team2, child.id))

        assertFailsWith<ForbiddenException> {
            useCase(
                parent.id,
                clubIdA,
                minClubRole = ClubRole.MEMBER,
                teamId = team1,
                allowPlayerOrParentAccess = true
            )
        }
        Unit
    }

    // --- P12: outsider (negative control) ---

    @Test
    fun outsiderWithNoRelationshipIsDenied() = runBlocking {
        val outsider = user("p12-outsider")
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubIdA) } returns null
        coEvery { teamRepository.getUserRoleInTeam(outsider.id, team1) } returns null

        assertFailsWith<ForbiddenException> {
            useCase(outsider.id, clubIdA, minClubRole = ClubRole.MEMBER, teamId = team1)
        }
        Unit
    }

    // --- P13: suspended member ---

    @Test
    fun suspendedMemberIsDeniedDespiteAValidClubRole() = runBlocking {
        // Fixed: CheckUserPermissionUseCase now denies any non-ACTIVE account outright.
        // See "issues to fix.md" -- "User status is never enforced."
        val suspended = user("p13-suspended-member", status = UserStatus.SUSPENDED)
        coEvery { userRepository.findById(suspended.id) } returns suspended
        coEvery { userRepository.getUserRoleInClub(suspended.id, clubIdA) } returns ClubRole.MEMBER

        assertFailsWith<ForbiddenException> {
            useCase(suspended.id, clubIdA, minClubRole = ClubRole.MEMBER)
        }
        Unit
    }
}
