package com.ricardofrango.squadify.domain.usecase

import kotlinx.datetime.LocalDate
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.ViewerAgeGroup
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `canCreateSeason` and `canCreateAgeGroup` on `ClubDetailsResponse` both mirror the identical
 * `minClubRole = COORDINATOR` gate `CreateSeasonUseCase` and `CreateAgeGroupsUseCase` each enforce
 * (same `CheckUserPermissionUseCase` call, used as a single non-throwing probe for both fields), so the
 * client can tell an empty seasons/age-groups list apart for an admin/coordinator ("nothing here yet,
 * create one") versus a plain member ("nothing here yet"). See `PROCESS_FLOWS.md` §3.3.
 */
class GetClubDetailsUseCaseTest {

    private val clubRepository = mockk<ClubRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository)
    private val seasonRepository = mockk<SeasonRepository>()
    init {
        coEvery { seasonRepository.getSeasonsByClub(any()) } returns emptyList()
        coEvery { ageGroupRepository.findByClubAndSeason(any(), any()) } returns emptyList()
    }
    private val useCase = GetClubDetailsUseCase(clubRepository, checkUserPermissionUseCase, transactionManager, userRepository, seasonRepository, ageGroupRepository)

    private val clubIdA = "club-a"
    private val club = Club(
        id = clubIdA,
        name = "Club A",
        status = ClubStatus.ACTIVE,
        createdAt = Instant.fromEpochMilliseconds(0),
        sport = SportType.FOOTBALL
    )

    private fun user(id: String, isPlatformAdmin: Boolean = false) = User(
        id = id,
        email = "$id@test.com",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        isPlatformAdmin = isPlatformAdmin,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun clubAdminCanCreateSeasons() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { clubRepository.getClubById(clubIdA) } returns club

        val result = useCase(admin.id, clubIdA)

        assertTrue(result.canEditClub)
    }

    @Test
    fun coordinatorCanCreateSeasons() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { clubRepository.getClubById(clubIdA) } returns club

        val result = useCase(coordinator.id, clubIdA)

        assertTrue(result.canEditClub)
    }

    @Test
    fun platformAdminCanCreateSeasons() = runBlocking {
        val platformAdmin = user("p1-platform-admin", isPlatformAdmin = true)
        coEvery { userRepository.findById(platformAdmin.id) } returns platformAdmin
        // Not a member: the permission check never needs a club role ("God Mode", see
        // CheckUserPermissionUseCase); the More panel's viewerRole falls back to Admin.
        coEvery { userRepository.getUserRoleInClub(platformAdmin.id, clubIdA) } returns null
        coEvery { clubRepository.getClubById(clubIdA) } returns club

        val result = useCase(platformAdmin.id, clubIdA)

        assertTrue(result.canEditClub)
        assertEquals(ClubRole.CLUB_ADMIN, result.viewerRole)
    }

    @Test
    fun plainMemberCannotCreateSeasonsButCanStillSeeClubDetails() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { clubRepository.getClubById(clubIdA) } returns club

        val result = useCase(member.id, clubIdA)

        assertFalse(result.canEditClub)
        assertEquals(result.club.id, clubIdA)
    }

    @Test
    fun nonMemberCannotSeeClubDetailsAtAll() = runBlocking {
        val outsider = user("p5-outsider")
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubIdA) } returns null
        coEvery { playerRepository.getPlayersByUser(outsider.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(outsider.id, clubIdA)
        }
        Unit
    }

    @Test
    fun missingClubThrowsNotFound() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { clubRepository.getClubById(clubIdA) } returns null

        assertFailsWith<NotFoundException> {
            useCase(admin.id, clubIdA)
        }
        Unit
    }

    // ---- The "More" panel's viewer info and scoped create actions (design 37) ----

    private val season = Season("s1", clubIdA, "2026/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 7, 1), LocalDate(2027, 6, 30), active = true)
    private val sub15 = AgeGroup("ag15", clubIdA, "Sub-15", "s1", 2012, 2012)
    private val sub17 = AgeGroup("ag17", clubIdA, "Sub-17", "s1", 2010, 2010)

    private fun coachOfSub15(): String {
        val coach = user("coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        coEvery { seasonRepository.getSeasonsByClub(clubIdA) } returns listOf(season)
        coEvery { ageGroupRepository.findByClubAndSeason(clubIdA, "s1") } returns listOf(sub17, sub15)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "ag15") } returns TeamRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, "ag17") } returns null
        return coach.id
    }

    @Test
    fun aCoachSeesTheirAgeGroupsAndMayCreateThereByDefault() = runBlocking {
        val coach = coachOfSub15()
        val result = useCase(coach, clubIdA)
        assertEquals(ClubRole.MEMBER, result.viewerRole)
        assertEquals(listOf(ViewerAgeGroup("ag15", "Sub-15", TeamRole.COACH)), result.viewerAgeGroups)
        assertEquals(listOf("ag15"), result.createTeamAgeGroupIds)
        assertEquals(listOf("ag15"), result.createPlayersAgeGroupIds)
    }

    @Test
    fun aClubThatMakesCreateTeamCoordinatorOnlyHidesItFromCoaches() = runBlocking {
        val coach = coachOfSub15()
        coEvery { clubPermissionRepository.getOverride(clubIdA, ConfigurablePermission.CREATE_TEAM) } returns
            ClubPermissionSetting(ConfigurablePermission.CREATE_TEAM, minTeamRole = null)
        val result = useCase(coach, clubIdA)
        assertEquals(emptyList(), result.createTeamAgeGroupIds)
        assertEquals(listOf("ag15"), result.createPlayersAgeGroupIds)
    }

    @Test
    fun coordinatorsGetNoScopedListsSinceTheyCanCreateAnywhere() = runBlocking {
        val coord = user("coord")
        coEvery { userRepository.findById(coord.id) } returns coord
        coEvery { userRepository.getUserRoleInClub(coord.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        val result = useCase(coord.id, clubIdA)
        assertEquals(true, result.canEditClub)
        assertEquals(ClubRole.COORDINATOR, result.viewerRole)
        assertEquals(emptyList(), result.createTeamAgeGroupIds)
    }

    @Test
    fun canManagePlayerStatusFollowsTheClubLevelSetting() = runBlocking {
        val coord = user("coord-status")
        coEvery { userRepository.findById(coord.id) } returns coord
        coEvery { userRepository.getUserRoleInClub(coord.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        assertTrue(useCase(coord.id, clubIdA).canManagePlayerStatus)

        // Admins only: the coordinator still edits the club, but no longer disables players.
        coEvery { clubPermissionRepository.getOverride(clubIdA, ConfigurablePermission.MANAGE_PLAYER_STATUS) } returns
            ClubPermissionSetting(ConfigurablePermission.MANAGE_PLAYER_STATUS, null, ClubRole.CLUB_ADMIN)
        val result = useCase(coord.id, clubIdA)
        assertTrue(result.canEditClub)
        assertFalse(result.canManagePlayerStatus)
    }
}
