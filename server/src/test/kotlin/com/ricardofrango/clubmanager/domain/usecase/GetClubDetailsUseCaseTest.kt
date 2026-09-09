package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.ClubRepository
import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ForbiddenException
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.Club
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.ClubStatus
import com.ricardofrango.clubmanager.model.User
import com.ricardofrango.clubmanager.model.UserStatus
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
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager)
    private val useCase = GetClubDetailsUseCase(clubRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"
    private val club = Club(
        id = clubIdA,
        name = "Club A",
        status = ClubStatus.ACTIVE,
        createdAt = Instant.fromEpochMilliseconds(0)
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
        // "God Mode" never even looks up a club role -- see CheckUserPermissionUseCase.
        coEvery { clubRepository.getClubById(clubIdA) } returns club

        val result = useCase(platformAdmin.id, clubIdA)

        assertTrue(result.canEditClub)
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
}
