package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubMemberSummary
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/** Remove from club (design 35): membership and age-group staff roles go, parent links stay. */
class RemoveClubMemberUseCaseTest {

    private val clubRepository = mockk<ClubRepository>()
    private val userRepository = mockk<UserRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val playerRepository = mockk<PlayerRepository>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val check = CheckUserPermissionUseCase(userRepository, mockk<TeamRepository>(), playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository)
    private val useCase = RemoveClubMemberUseCase(clubRepository, ageGroupRepository, userRepository, check, transactionManager)

    private val club = "club-a"

    private fun caller(id: String, role: ClubRole?, platformAdmin: Boolean = false): String {
        coEvery { userRepository.findById(id) } returns User(id = id, email = "$id@t.com", firstName = "F", lastName = "L", status = UserStatus.ACTIVE, isPlatformAdmin = platformAdmin, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.getUserRoleInClub(id, club) } returns role
        return id
    }

    private fun members(vararg pairs: Pair<String, ClubRole>) {
        coEvery { clubRepository.getClubMembers(club) } returns pairs.map { (id, role) -> ClubMemberSummary(id, "F", "L", "$id@t.com", role) }
        coEvery { clubRepository.removeMember(club, any()) } returns true
        coEvery { ageGroupRepository.removeStaffFromClub(club, any()) } returns 1
    }

    @Test
    fun coordinatorRemovesAMemberAndTheirStaffRolesButNotTheirPlayerLinks() = runBlocking {
        val me = caller("coord", ClubRole.COORDINATOR)
        members(me to ClubRole.COORDINATOR, "m" to ClubRole.MEMBER)

        useCase(me, club, "m")

        coVerify { ageGroupRepository.removeStaffFromClub(club, "m") }
        coVerify { clubRepository.removeMember(club, "m") }
        // Parent links aren't touched: the use case has no access to UserPlayers at all.
    }

    @Test
    fun aPlainMemberCannotRemoveAnyone() = runBlocking {
        val me = caller("m1", ClubRole.MEMBER)
        members(me to ClubRole.MEMBER, "m2" to ClubRole.MEMBER)
        assertFailsWith<ForbiddenException> { useCase(me, club, "m2") }
        Unit
    }

    @Test
    fun nobodyRemovesThemselves() = runBlocking {
        val me = caller("admin", ClubRole.CLUB_ADMIN)
        members(me to ClubRole.CLUB_ADMIN, "admin2" to ClubRole.CLUB_ADMIN)
        assertFailsWith<ValidationException> { useCase(me, club, me) }
        Unit
    }

    @Test
    fun aCoordinatorCannotRemoveAnAdmin() = runBlocking {
        val me = caller("coord", ClubRole.COORDINATOR)
        members(me to ClubRole.COORDINATOR, "admin" to ClubRole.CLUB_ADMIN, "admin2" to ClubRole.CLUB_ADMIN)
        assertFailsWith<ForbiddenException> { useCase(me, club, "admin") }
        Unit
    }

    @Test
    fun anAdminCanRemoveAnotherAdmin() = runBlocking {
        val me = caller("admin", ClubRole.CLUB_ADMIN)
        members(me to ClubRole.CLUB_ADMIN, "admin2" to ClubRole.CLUB_ADMIN)
        useCase(me, club, "admin2")
        coVerify { clubRepository.removeMember(club, "admin2") }
    }

    @Test
    fun thePlatformAdminCannotRemoveTheLastAdmin() = runBlocking {
        val me = caller("platform", null, platformAdmin = true)
        members("admin" to ClubRole.CLUB_ADMIN, "m" to ClubRole.MEMBER)
        assertFailsWith<ConflictException> { useCase(me, club, "admin") }
        Unit
    }

    @Test
    fun aNonMemberIsNotFound() = runBlocking {
        val me = caller("admin", ClubRole.CLUB_ADMIN)
        members(me to ClubRole.CLUB_ADMIN)
        assertFailsWith<NotFoundException> { useCase(me, club, "ghost") }
        Unit
    }
}
