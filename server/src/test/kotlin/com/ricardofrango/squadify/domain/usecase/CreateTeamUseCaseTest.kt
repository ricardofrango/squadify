package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.CreateTeamRequest
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Covers the "Age groups & seasons" team-creation rows of `PERMISSION_TEST_MATRIX.md`.
 * CreateTeamUseCase treats CLUB_ADMIN and COORDINATOR equivalently (both always clear the
 * `minClubRole = ClubRole.COORDINATOR` floor) and validates that `ageGroupId` actually belongs to
 * `clubId`/`seasonId` before creating the team (and before the permission check even runs, since that
 * check trusts this `ageGroupId` -- see `CreateTeamUseCase`'s own comment on the cross-club spoofing
 * risk this ordering avoids). `ConfigurablePermission.CREATE_TEAM` (§37 in TECHNICAL_DECISIONS.md) means
 * a coach of this specific age group can also create a team in it now, by default -- not just a
 * coordinator/admin.
 */
class CreateTeamUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val fcmTokenRepository = mockk<FcmTokenRepository>()
    private val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository)
    private val useCase = CreateTeamUseCase(
        teamRepository, ageGroupRepository, clubRepository, fcmTokenRepository, checkUserPermissionUseCase, pushNotificationService, transactionManager
    )

    private val clubIdA = "club-a"

    private val consistentAgeGroup = AgeGroup(
        id = "age-group-1",
        clubId = clubIdA,
        name = "U12",
        seasonId = "season-1",
        startYear = 2014,
        endYear = 2014
    )

    private val club = Club(
        id = clubIdA,
        name = "Test FC",
        status = ClubStatus.ACTIVE,
        createdAt = Instant.fromEpochMilliseconds(0),
        sport = SportType.FOOTBALL
    )

    private val dummyTeam = Team(
        id = "generated-team-id",
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
    fun coordinatorCanCreateATeamJustLikeAnAdmin() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        coEvery { clubRepository.getClubMembers(clubIdA) } returns emptyList()
        coEvery { fcmTokenRepository.getTokensForUsers(emptyList()) } returns emptyMap()
        coEvery { teamRepository.createTeam(any()) } returns dummyTeam
        val request = CreateTeamRequest(clubId = clubIdA, ageGroupId = "age-group-1", seasonId = "season-1", name = "Team 1")

        useCase(coordinator.id, request)
        Unit
    }

    @Test
    fun plainMemberCannotCreateATeam() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, "age-group-1") } returns null
        val request = CreateTeamRequest(clubId = clubIdA, ageGroupId = "age-group-1", seasonId = "season-1", name = "Team 1")

        assertFailsWith<ForbiddenException> {
            useCase(member.id, request)
        }
        Unit
    }

    @Test
    fun ageGroupCoachCanCreateATeamInTheirOwnAgeGroup() = runBlocking {
        // The concrete fix behind §37: a coach who isn't a club coordinator/admin can now create a
        // team in an age group they coach, since CREATE_TEAM defaults to Coach like its siblings.
        val coach = user("p8-coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "age-group-1") } returns TeamRole.COACH
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        coEvery { clubRepository.getClubMembers(clubIdA) } returns emptyList()
        coEvery { fcmTokenRepository.getTokensForUsers(emptyList()) } returns emptyMap()
        coEvery { teamRepository.createTeam(any()) } returns dummyTeam
        val request = CreateTeamRequest(clubId = clubIdA, ageGroupId = "age-group-1", seasonId = "season-1", name = "Team 1")

        useCase(coach.id, request)
        Unit
    }

    @Test
    fun ageGroupStaffCannotCreateATeamByDefault() = runBlocking {
        val staff = user("p9-staff")
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, "age-group-1") } returns TeamRole.STAFF
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        val request = CreateTeamRequest(clubId = clubIdA, ageGroupId = "age-group-1", seasonId = "season-1", name = "Team 1")

        assertFailsWith<ForbiddenException> {
            useCase(staff.id, request)
        }
        Unit
    }

    @Test
    fun aNonexistentAgeGroupIsNotFound() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns null
        val request = CreateTeamRequest(clubId = clubIdA, ageGroupId = "age-group-1", seasonId = "season-1", name = "Team 1")

        assertFailsWith<NotFoundException> {
            useCase(admin.id, request)
        }
        Unit
    }

    @Test
    fun aNonexistentClubIsNotFound() = runBlocking {
        // The club is now always fetched (needed for the push notification's club name, not just to
        // validate an optional teamSize) -- no teamSize needed in the request to reach this branch.
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        coEvery { clubRepository.getClubById(clubIdA) } returns null
        val request = CreateTeamRequest(clubId = clubIdA, ageGroupId = "age-group-1", seasonId = "season-1", name = "Team 1")

        assertFailsWith<NotFoundException> {
            useCase(admin.id, request)
        }
        Unit
    }

    @Test
    fun ageGroupFromADifferentSeasonIsRejected() = runBlocking {
        // Fixed: CreateTeamUseCase now checks that the age group actually belongs to the given club and
        // season before creating the team. See "issues to fix.md".
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-from-season-X") } returns AgeGroup(
            id = "age-group-from-season-X",
            clubId = clubIdA,
            name = "U14",
            seasonId = "season-X-actual", // deliberately inconsistent with the request's seasonId below
            startYear = 2012,
            endYear = 2012
        )
        val request = CreateTeamRequest(
            clubId = clubIdA,
            ageGroupId = "age-group-from-season-X",
            seasonId = "season-Y", // deliberately inconsistent with the age group's real season
            name = "Team 1"
        )

        assertFailsWith<ValidationException> {
            useCase(admin.id, request)
        }
        Unit
    }

    @Test
    fun creatingATeamNotifiesTheClub() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        coEvery { clubRepository.getClubMembers(clubIdA) } returns listOf(
            ClubMemberSummary("u1", "A", "B", "a@b.com", ClubRole.MEMBER)
        )
        coEvery { fcmTokenRepository.getTokensForUsers(listOf("u1")) } returns mapOf("u1" to listOf("token-1"))
        coEvery { teamRepository.createTeam(any()) } returns dummyTeam
        val request = CreateTeamRequest(clubId = clubIdA, ageGroupId = "age-group-1", seasonId = "season-1", name = "Team 1")

        useCase(admin.id, request)

        coVerify { pushNotificationService.sendTeamCreated(listOf("token-1"), PushLink.team(clubIdA, dummyTeam.id), listOf("Team 1"), "U12", "Test FC") }
    }

    @Test
    fun noClubMemberHasATokenSendsNothing() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        coEvery { clubRepository.getClubMembers(clubIdA) } returns emptyList()
        coEvery { fcmTokenRepository.getTokensForUsers(emptyList()) } returns emptyMap()
        coEvery { teamRepository.createTeam(any()) } returns dummyTeam
        val request = CreateTeamRequest(clubId = clubIdA, ageGroupId = "age-group-1", seasonId = "season-1", name = "Team 1")

        useCase(admin.id, request)

        coVerify(exactly = 0) { pushNotificationService.sendTeamCreated(any(), any(), any(), any(), any()) }
    }
}
