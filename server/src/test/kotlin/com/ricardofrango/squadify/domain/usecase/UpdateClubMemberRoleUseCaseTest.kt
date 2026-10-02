package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateClubMemberRoleRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
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
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository)
    private val useCase = UpdateClubMemberRoleUseCase(clubRepository, userRepository, checkUserPermissionUseCase, transactionManager)

    private fun members(vararg pairs: Pair<String, ClubRole>) {
        coEvery { clubRepository.getClubMembers(clubIdA) } returns pairs.map { (id, role) -> ClubMemberSummary(id, "F", "L", "$id@t.com", role) }
    }

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
        members(admin.id to ClubRole.CLUB_ADMIN, targetUserId to ClubRole.MEMBER)

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
        members(coordinator.id to ClubRole.COORDINATOR, targetUserId to ClubRole.MEMBER)

        useCase(coordinator.id, clubIdA, targetUserId, UpdateClubMemberRoleRequest(role = ClubRole.COORDINATOR))
        Unit
    }

    @Test
    fun targetingSomeoneWhoIsNotAClubMemberIsNotFound() = runBlocking {
        // updateMemberRole returns false when no row matched (clubId, targetUserId) -- e.g. a stale
        // UI, a typo'd id, or someone who was just removed from the club.
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        members(admin.id to ClubRole.CLUB_ADMIN)

        assertFailsWith<NotFoundException> {
            useCase(admin.id, clubIdA, "not-a-member", UpdateClubMemberRoleRequest(role = ClubRole.COORDINATOR))
        }
        Unit
    }

    @Test
    fun aCoordinatorCannotGrantOrRemoveAdmin() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        members(coordinator.id to ClubRole.COORDINATOR, targetUserId to ClubRole.MEMBER, "admin-1" to ClubRole.CLUB_ADMIN, "admin-2" to ClubRole.CLUB_ADMIN)

        assertFailsWith<ForbiddenException> {
            useCase(coordinator.id, clubIdA, targetUserId, UpdateClubMemberRoleRequest(role = ClubRole.CLUB_ADMIN))
        }
        assertFailsWith<ForbiddenException> {
            useCase(coordinator.id, clubIdA, "admin-1", UpdateClubMemberRoleRequest(role = ClubRole.MEMBER))
        }
        Unit
    }

    @Test
    fun theLastAdminCannotBeDemotedEvenByThemselves() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        members(admin.id to ClubRole.CLUB_ADMIN, targetUserId to ClubRole.MEMBER)

        assertFailsWith<ConflictException> {
            useCase(admin.id, clubIdA, admin.id, UpdateClubMemberRoleRequest(role = ClubRole.COORDINATOR))
        }
        Unit
    }

    @Test
    fun anAdminCanStepDownWhenAnotherAdminExists() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        members(admin.id to ClubRole.CLUB_ADMIN, "admin-2" to ClubRole.CLUB_ADMIN)
        coEvery { clubRepository.updateMemberRole(clubIdA, admin.id, ClubRole.COORDINATOR) } returns true

        useCase(admin.id, clubIdA, admin.id, UpdateClubMemberRoleRequest(role = ClubRole.COORDINATOR))
        Unit
    }
}
