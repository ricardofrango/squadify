package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.UpdateClubMemberRoleRequest
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Covers the "Change a Club A member's role" rows of `PERMISSION_TEST_MATRIX.md`. Same coordinator-
 * exclusion gap as `UpdateClubUseCaseTest`: UpdateClubMemberRoleUseCase requires
 * `minClubRole = ClubRole.CLUB_ADMIN`, excluding COORDINATOR. See "issues to fix.md".
 */
class UpdateClubMemberRoleUseCaseTest {

    private val clubRepository = mockk<ClubRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, transactionManager)
    private val useCase = UpdateClubMemberRoleUseCase(clubRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"
    private val targetUserId = "target-user"

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
    fun clubAdminCanChangeAMembersRole() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { clubRepository.updateMemberRole(clubIdA, targetUserId, ClubRole.COORDINATOR) } returns true

        useCase(admin.id, clubIdA, targetUserId, UpdateClubMemberRoleRequest(role = ClubRole.COORDINATOR))
        Unit
    }

    @Test
    fun plainMemberCannotChangeAMembersRole() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER

        assertFailsWith<ForbiddenException> {
            useCase(member.id, clubIdA, targetUserId, UpdateClubMemberRoleRequest(role = ClubRole.COORDINATOR))
        }
        Unit
    }

    @Test
    fun coordinatorCanChangeAMembersRole() = runBlocking {
        // Fixed: same admin/coordinator equivalence gap as UpdateClubUseCaseTest -- this use case now
        // passes minClubRole = COORDINATOR instead of CLUB_ADMIN. See "issues to fix.md".
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { clubRepository.updateMemberRole(clubIdA, targetUserId, ClubRole.COORDINATOR) } returns true

        useCase(coordinator.id, clubIdA, targetUserId, UpdateClubMemberRoleRequest(role = ClubRole.COORDINATOR))
        Unit
    }
}
