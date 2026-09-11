package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.ClubRepository
import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ForbiddenException
import com.ricardofrango.clubmanager.model.Club
import com.ricardofrango.clubmanager.model.SportType
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.ClubStatus
import com.ricardofrango.clubmanager.model.UpdateClubRequest
import com.ricardofrango.clubmanager.model.User
import com.ricardofrango.clubmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Covers the "Update Club A profile" rows of `PERMISSION_TEST_MATRIX.md`. Admin and coordinator are
 * supposed to be fully equivalent until billing exists, but UpdateClubUseCase requires
 * `minClubRole = ClubRole.CLUB_ADMIN` specifically, excluding COORDINATOR. See "issues to fix.md".
 */
class UpdateClubUseCaseTest {

    private val clubRepository = mockk<ClubRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager)
    private val useCase = UpdateClubUseCase(clubRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"
    private val club = Club(
        id = clubIdA,
        name = "Club A",
        status = ClubStatus.ACTIVE,
        createdAt = Instant.fromEpochMilliseconds(0),
        sport = SportType.FOOTBALL
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
    fun clubAdminCanUpdateTheClubProfile() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        coEvery { clubRepository.updateClub(any()) } returns true

        useCase(admin.id, clubIdA, UpdateClubRequest(name = "New Name"))
        Unit
    }

    @Test
    fun plainMemberCannotUpdateTheClubProfile() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { clubRepository.getClubById(clubIdA) } returns club

        assertFailsWith<ForbiddenException> {
            useCase(member.id, clubIdA, UpdateClubRequest(name = "New Name"))
        }
        Unit
    }

    @Test
    fun coordinatorCanUpdateTheClubProfile() = runBlocking {
        // Fixed: the only planned difference between CLUB_ADMIN and COORDINATOR is billing access, not
        // implemented yet, so a coordinator can now do this too. UpdateClubUseCase now passes
        // minClubRole = COORDINATOR instead of CLUB_ADMIN. See "issues to fix.md".
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        coEvery { clubRepository.updateClub(any()) } returns true

        useCase(coordinator.id, clubIdA, UpdateClubRequest(name = "New Name"))
        Unit
    }
}
