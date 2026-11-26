package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.service.ClubPushAudience
import com.sportssoftw.squadcall.domain.access.ClubAccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.FcmTokenRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.service.PushNotificationService
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.PushLink
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.model.Club
import com.sportssoftw.squadcall.model.ClubMemberSummary
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.ClubStatus
import com.sportssoftw.squadcall.model.SportType
import com.sportssoftw.squadcall.model.CreateTeamsRequest
import com.sportssoftw.squadcall.model.Team
import com.sportssoftw.squadcall.model.TeamItemRequest
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Batch team creation shares CreateTeamUseCase's rules (`ConfigurablePermission.CREATE_TEAM` gate,
 * defaulting to Coach -- see §37 in TECHNICAL_DECISIONS.md; the age group must belong to the request's
 * club and season), applied once for the whole batch, plus a blank-name check on every row. Mirrors
 * CreateTeamUseCaseTest.
 */
class CreateTeamsUseCaseTest {

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
    private val useCase = CreateTeamsUseCase(
        teamRepository, ageGroupRepository, clubRepository, fcmTokenRepository, ClubAccessPolicy(checkUserPermissionUseCase), pushNotificationService, transactionManager,
        ClubPushAudience(clubRepository, ageGroupRepository)
    )

    private val clubIdA = "club-a"

    init {
        coEvery { clubRepository.getClubById(clubIdA) } returns Club(
            id = clubIdA, name = "C", status = ClubStatus.ACTIVE,
            createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL
        )
        // Default: no club members found, so tests that don't care about the push simply never trigger
        // one -- dedicated tests below override this to actually exercise sendTeamCreated.
        coEvery { clubRepository.getClubMembers(clubIdA) } returns emptyList()
        coEvery { fcmTokenRepository.getTokensForUsers(emptyList()) } returns emptyMap()
    }

    private val consistentAgeGroup = AgeGroup(
        id = "age-group-1",
        clubId = clubIdA,
        name = "U12",
        seasonId = "season-1",
        startYear = 2014,
        endYear = 2014
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

    private fun request(vararg names: String) = CreateTeamsRequest(
        clubId = clubIdA,
        seasonId = "season-1",
        ageGroupId = "age-group-1",
        items = names.map { TeamItemRequest(name = it) }
    )

    @Test
    fun coordinatorCreatesEveryTeamInTheBatch() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        val created = slot<List<Team>>()
        coEvery { teamRepository.createTeams(capture(created)) } answers { created.captured }

        val result = useCase(coordinator.id, request("Team A", "Team B", "Team C"))

        assertEquals(listOf("Team A", "Team B", "Team C"), result.map { it.name })
        assertEquals(3, created.captured.size)
        Unit
    }

    @Test
    fun plainMemberCannotCreateTeams() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, "age-group-1") } returns null

        assertFailsWith<ForbiddenException> {
            useCase(member.id, request("Team A"))
        }
        Unit
    }

    @Test
    fun ageGroupCoachCanCreateTheWholeBatch() = runBlocking {
        // The concrete fix behind §37: a coach who isn't a club coordinator/admin can now create teams
        // in an age group they coach, since CREATE_TEAM defaults to Coach like its siblings.
        val coach = user("p8-coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "age-group-1") } returns AgeGroupRole.COACH
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        val created = slot<List<Team>>()
        coEvery { teamRepository.createTeams(capture(created)) } answers { created.captured }

        val result = useCase(coach.id, request("Team A", "Team B"))

        assertEquals(listOf("Team A", "Team B"), result.map { it.name })
        Unit
    }

    @Test
    fun ageGroupStaffCannotCreateTeamsByDefault() = runBlocking {
        val staff = user("p9-staff")
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, "age-group-1") } returns AgeGroupRole.STAFF
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup

        assertFailsWith<ForbiddenException> {
            useCase(staff.id, request("Team A"))
        }
        Unit
    }

    @Test
    fun aBlankTeamNameInTheBatchIsRejected() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup

        assertFailsWith<ValidationException> {
            useCase(admin.id, request("Team A", "  "))
        }
        Unit
    }

    @Test
    fun anEmptyBatchIsRejected() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN

        assertFailsWith<ValidationException> {
            useCase(admin.id, request())
        }
        Unit
    }

    @Test
    fun aNonexistentAgeGroupIsNotFound() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns null

        assertFailsWith<NotFoundException> {
            useCase(admin.id, request("Team A"))
        }
        Unit
    }

    @Test
    fun aNonexistentClubIsNotFound() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        coEvery { clubRepository.getClubById(clubIdA) } returns null

        assertFailsWith<NotFoundException> {
            useCase(admin.id, request("Team A"))
        }
        Unit
    }

    @Test
    fun ageGroupFromADifferentSeasonIsRejected() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup.copy(
            seasonId = "season-X" // inconsistent with the request's season-1
        )

        assertFailsWith<ValidationException> {
            useCase(admin.id, request("Team A"))
        }
        Unit
    }

    @Test
    fun creatingABatchNotifiesTheClubWithEveryTeamName() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        coEvery { clubRepository.getClubMembers(clubIdA) } returns listOf(
            ClubMemberSummary("u1", "A", "B", "a@b.com", ClubRole.MEMBER)
        )
        coEvery { fcmTokenRepository.getTokensForUsers(listOf("u1")) } returns mapOf("u1" to listOf("token-1"))
        val created = slot<List<Team>>()
        coEvery { teamRepository.createTeams(capture(created)) } answers { created.captured }

        useCase(coordinator.id, request("Team A", "Team B"))

        coVerify { pushNotificationService.sendTeamCreated(listOf("token-1"), PushLink.ageGroup(clubIdA, "age-group-1"), listOf("Team A", "Team B"), "U12", "C") }
    }

    @Test
    fun noClubMemberHasATokenSendsNothing() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        val created = slot<List<Team>>()
        coEvery { teamRepository.createTeams(capture(created)) } answers { created.captured }

        useCase(coordinator.id, request("Team A"))

        coVerify(exactly = 0) { pushNotificationService.sendTeamCreated(any(), any(), any(), any(), any()) }
    }
}
