package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Permission model after the player/coach -> age-group move: the team-role branch became an
 * age-group-staff branch (`AgeGroupStaff`), and the player/parent link is now "a linked player sits
 * in this age group's pool" (`AgeGroupPlayers`). A `teamId` passed by a caller is resolved to its
 * age group internally. Personas follow `PERMISSION_TEST_MATRIX.md`.
 */
class CheckUserPermissionUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val clubPermissionRepository = mockk<ClubPermissionRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val useCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )

    private val clubIdA = "club-a"
    private val clubIdB = "club-b"
    private val ag1 = "age-group-1" // Club A
    private val ag2 = "age-group-2" // Club A
    private val ag3 = "age-group-3" // Club A - not assigned to any persona below
    private val ag5 = "age-group-5" // Club B

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

    @Test
    fun platformAdminBypassesEveryCheckEvenWithoutClubRole() = runBlocking {
        val admin = user("p1-super-admin", isPlatformAdmin = true)
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns null

        useCase(admin.id, clubIdA, minClubRole = ClubRole.CLUB_ADMIN)
        Unit
    }

    @Test
    fun unknownUserIsDenied() = runBlocking {
        coEvery { userRepository.findById("ghost") } returns null

        assertFailsWith<ForbiddenException> {
            useCase("ghost", clubIdA, minClubRole = ClubRole.MEMBER)
        }
        Unit
    }

    @Test
    fun clubAdminPassesClubAdminRequirement() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN

        useCase(admin.id, clubIdA, minClubRole = ClubRole.CLUB_ADMIN)
        Unit
    }

    @Test
    fun coordinatorIsDeniedForClubAdminOnlyRequirement() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR

        assertFailsWith<ForbiddenException> {
            useCase(coordinator.id, clubIdA, minClubRole = ClubRole.CLUB_ADMIN)
        }
        Unit
    }

    @Test
    fun plainMemberWithNoAgeGroupContextPassesMemberRequirement() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER

        useCase(member.id, clubIdA, minClubRole = ClubRole.MEMBER)
        Unit
    }

    @Test
    fun ageGroupCoachPassesWhenCallerSuppliesMinAgeGroupRole() = runBlocking {
        val coach = user("p5-coach-ag1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH

        useCase(coach.id, clubIdA, minClubRole = ClubRole.MEMBER, minAgeGroupRole = AgeGroupRole.COACH, ageGroupId = ag1)
        Unit
    }

    @Test
    fun staffIsDeniedForAnAgeGroupTheyDontBelongTo() = runBlocking {
        val staff = user("p6-staff-ag2")
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns null

        assertFailsWith<ForbiddenException> {
            useCase(staff.id, clubIdA, minClubRole = ClubRole.MEMBER, minAgeGroupRole = AgeGroupRole.STAFF, ageGroupId = ag1)
        }
        Unit
    }

    @Test
    fun aTeamIdIsResolvedToItsAgeGroupForTheStaffCheck() = runBlocking {
        // Match use cases still pass teamId = match.teamId; the check must resolve it to the team's
        // age group before consulting AgeGroupStaff.
        val coach = user("p5-coach-via-team")
        val team1 = "team-1"
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamById(team1) } returns Team(
            id = team1, clubId = clubIdA, ageGroupId = ag1, seasonId = "s1", name = "AG1 A", code = null, active = true
        )
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH

        useCase(coach.id, clubIdA, minClubRole = ClubRole.MEMBER, minAgeGroupRole = AgeGroupRole.COACH, teamId = team1)
        Unit
    }

    @Test
    fun multiRoleMemberPassesForEachAssignedAgeGroupOnly() = runBlocking {
        val member = user("p7-multi-role")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ag1) } returns AgeGroupRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ag2) } returns AgeGroupRole.STAFF
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ag3) } returns null

        useCase(member.id, clubIdA, minClubRole = ClubRole.MEMBER, minAgeGroupRole = AgeGroupRole.COACH, ageGroupId = ag1)
        useCase(member.id, clubIdA, minClubRole = ClubRole.MEMBER, minAgeGroupRole = AgeGroupRole.STAFF, ageGroupId = ag2)
        assertFailsWith<ForbiddenException> {
            useCase(member.id, clubIdA, minClubRole = ClubRole.MEMBER, minAgeGroupRole = AgeGroupRole.STAFF, ageGroupId = ag3)
        }
        Unit
    }

    @Test
    fun crossClubCoachPassesIndependentlyInBothClubs() = runBlocking {
        val coach = user("p8-cross-club-coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdB) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag5) } returns AgeGroupRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag3) } returns null

        useCase(coach.id, clubIdA, minClubRole = ClubRole.MEMBER, minAgeGroupRole = AgeGroupRole.COACH, ageGroupId = ag1)
        useCase(coach.id, clubIdB, minClubRole = ClubRole.MEMBER, minAgeGroupRole = AgeGroupRole.COACH, ageGroupId = ag5)
        assertFailsWith<ForbiddenException> {
            useCase(coach.id, clubIdA, minClubRole = ClubRole.MEMBER, minAgeGroupRole = AgeGroupRole.COACH, ageGroupId = ag3)
        }
        Unit
    }

    @Test
    fun parentIsGrantedAccessToChildsAgeGroupWhenReadPathOptsIn() = runBlocking {
        val parent = user("p10-parent-of-one-child")
        val child = player("child-1", clubIdA)
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)
        coEvery { playerRepository.getPlayersByAgeGroup(ag1) } returns listOf(child)

        useCase(parent.id, clubIdA, minClubRole = ClubRole.MEMBER, ageGroupId = ag1, allowPlayerOrParentAccess = true)
        Unit
    }

    @Test
    fun parentLinkIsIgnoredWhenCallerDoesNotOptIn() = runBlocking {
        val parent = user("p10-parent-of-one-child")
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, ag1) } returns null

        assertFailsWith<ForbiddenException> {
            useCase(parent.id, clubIdA, minClubRole = ClubRole.MEMBER, ageGroupId = ag1)
        }
        Unit
    }

    @Test
    fun parentLinkedToADifferentClubIsDeniedEvenWithOptIn() = runBlocking {
        val parent = user("p10-parent-other-club")
        val child = player("child-2", clubIdB)
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)

        assertFailsWith<ForbiddenException> {
            useCase(parent.id, clubIdA, minClubRole = ClubRole.MEMBER, ageGroupId = ag1, allowPlayerOrParentAccess = true)
        }
        Unit
    }

    @Test
    fun parentLinkedToTheRightClubButADifferentAgeGroupIsDenied() = runBlocking {
        val parent = user("p10-parent-different-ag")
        val child = player("child-3", clubIdA)
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)
        coEvery { playerRepository.getPlayersByAgeGroup(ag1) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(parent.id, clubIdA, minClubRole = ClubRole.MEMBER, ageGroupId = ag1, allowPlayerOrParentAccess = true)
        }
        Unit
    }

    @Test
    fun outsiderWithNoRelationshipIsDenied() = runBlocking {
        val outsider = user("p12-outsider")
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubIdA) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(outsider.id, ag1) } returns null

        assertFailsWith<ForbiddenException> {
            useCase(outsider.id, clubIdA, minClubRole = ClubRole.MEMBER, ageGroupId = ag1)
        }
        Unit
    }

    @Test
    fun suspendedMemberIsDeniedDespiteAValidClubRole() = runBlocking {
        val suspended = user("p13-suspended-member", status = UserStatus.SUSPENDED)
        coEvery { userRepository.findById(suspended.id) } returns suspended
        coEvery { userRepository.getUserRoleInClub(suspended.id, clubIdA) } returns ClubRole.MEMBER

        assertFailsWith<ForbiddenException> {
            useCase(suspended.id, clubIdA, minClubRole = ClubRole.MEMBER)
        }
        Unit
    }

    // Keeps AgeGroupPlayer referenced so its shape stays test-covered alongside the pool checks above.
    @Suppress("unused")
    private fun link(id: String, ageGroupId: String, playerId: String) = AgeGroupPlayer(id, ageGroupId, playerId)

    // --- configurablePermission resolution (see TECHNICAL_DECISIONS.md on configurable permissions) ---

    @Test
    fun configurablePermissionFallsBackToItsDefaultWhenClubHasNoOverride() = runBlocking {
        // CREATE_PLAYERS defaults to COACH -- no override row means a coach should still pass.
        val coach = user("coach-default")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { clubPermissionRepository.getOverride(clubIdA, ConfigurablePermission.CREATE_PLAYERS) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH

        useCase(
            coach.id, clubIdA,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.CREATE_PLAYERS,
            ageGroupId = ag1
        )
        Unit
    }

    @Test
    fun configurablePermissionOverrideToStaffAdmitsAStaffMember() = runBlocking {
        // MANAGE_LIVE_MATCH defaults to STAFF already, but this exercises the override path explicitly
        // (as opposed to the default-fallback path above) with an explicit STAFF override row.
        val staff = user("staff-override")
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { clubPermissionRepository.getOverride(clubIdA, ConfigurablePermission.MANAGE_LIVE_MATCH) } returns
            ClubPermissionSetting(ConfigurablePermission.MANAGE_LIVE_MATCH, AgeGroupRole.STAFF)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns AgeGroupRole.STAFF

        useCase(
            staff.id, clubIdA,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.MANAGE_LIVE_MATCH,
            ageGroupId = ag1
        )
        Unit
    }

    @Test
    fun configurablePermissionOverrideToCoordinatorOnlyDeniesACoachWhoWouldOtherwisePass() = runBlocking {
        // An override row with minAgeGroupRole = null means "coordinator-only" -- must NOT be treated the
        // same as "no override" (which would fall back to the COACH default and admit this coach).
        val coach = user("coach-locked-out")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { clubPermissionRepository.getOverride(clubIdA, ConfigurablePermission.CREATE_PLAYERS) } returns
            ClubPermissionSetting(ConfigurablePermission.CREATE_PLAYERS, null)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH

        assertFailsWith<ForbiddenException> {
            useCase(
                coach.id, clubIdA,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.CREATE_PLAYERS,
                ageGroupId = ag1
            )
        }
        Unit
    }

    @Test
    fun coordinatorPassesAConfigurablePermissionRegardlessOfItsStaffSetting() = runBlocking {
        // The minClubRole = COORDINATOR floor is independent of configurablePermission entirely -- a
        // coordinator passes via step 2 before the club-permission repository is even consulted.
        val coordinator = user("coordinator-always")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR

        useCase(
            coordinator.id, clubIdA,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.MANAGE_MATCH
        )
        coVerify(exactly = 0) { clubPermissionRepository.getOverride(any(), any()) }
    }

    // ---- Club-level configurable action: MANAGE_PLAYER_STATUS (a club role, not an age-group role) ----

    private suspend fun managePlayerStatus(userId: String) = useCase(
        userId, clubIdA,
        minClubRole = ClubRole.COORDINATOR,
        configurablePermission = ConfigurablePermission.MANAGE_PLAYER_STATUS
    )

    @Test
    fun aCoordinatorManagesPlayerStatusByDefaultButAnAgeGroupCoachNever() = runBlocking<Unit> {
        coEvery { clubPermissionRepository.getOverride(clubIdA, ConfigurablePermission.MANAGE_PLAYER_STATUS) } returns null
        val coordinator = user("coord-status")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        managePlayerStatus(coordinator.id)

        val coach = user("coach-status")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, any()) } returns AgeGroupRole.COACH
        assertFailsWith<ForbiddenException> { managePlayerStatus(coach.id) }
    }

    @Test
    fun adminsOnlyShutsOutTheCoordinatorButNotTheAdmin() = runBlocking<Unit> {
        coEvery { clubPermissionRepository.getOverride(clubIdA, ConfigurablePermission.MANAGE_PLAYER_STATUS) } returns
            ClubPermissionSetting(ConfigurablePermission.MANAGE_PLAYER_STATUS, null, ClubRole.CLUB_ADMIN)
        val coordinator = user("coord-locked")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        assertFailsWith<ForbiddenException> { managePlayerStatus(coordinator.id) }

        val admin = user("admin-status")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        managePlayerStatus(admin.id)
    }
}
