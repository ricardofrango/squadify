package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UpdateClubPermissionsRequest
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
 * Covers the one rule that is NOT itself configurable: changing a club's permission settings always
 * requires COORDINATOR+, regardless of what any of the settings themselves say -- per the developer's
 * explicit instruction ("only coordinator and club admin can change permissions... not configurable").
 */
class UpdateClubPermissionsUseCaseTest {

    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = UpdateClubPermissionsUseCase(clubPermissionRepository, checkUserPermissionUseCase, transactionManager)

    private val clubId = "club-1"

    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    private fun request() = UpdateClubPermissionsRequest(
        settings = listOf(ClubPermissionSetting(ConfigurablePermission.CREATE_PLAYERS, TeamRole.STAFF))
    )

    @Test
    fun coordinatorCanChangeSettings() = runBlocking {
        val coordinator = user("coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubId) } returns ClubRole.COORDINATOR

        useCase(coordinator.id, clubId, request())

        coVerify { clubPermissionRepository.replaceOverrides(clubId, request().settings) }
    }

    @Test
    fun clubAdminCanChangeSettings() = runBlocking {
        val admin = user("admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubId) } returns ClubRole.CLUB_ADMIN

        useCase(admin.id, clubId, request())

        coVerify { clubPermissionRepository.replaceOverrides(clubId, request().settings) }
    }

    @Test
    fun aPlainMemberCannotChangeSettings() = runBlocking {
        val member = user("member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER

        assertFailsWith<ForbiddenException> { useCase(member.id, clubId, request()) }
        coVerify(exactly = 0) { clubPermissionRepository.replaceOverrides(any(), any()) }
    }

    @Test
    fun anAgeGroupCoachCannotChangeSettingsEitherEvenThoughTheyCanCreatePlayers() = runBlocking {
        // The whole point: being allowed to DO a configurable action (e.g. CREATE_PLAYERS as a coach)
        // never implies being allowed to CHANGE that action's threshold.
        val coach = user("coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER

        assertFailsWith<ForbiddenException> { useCase(coach.id, clubId, request()) }
        coVerify(exactly = 0) { clubPermissionRepository.replaceOverrides(any(), any()) }
    }

    @Test
    fun duplicateActionsInTheRequestAreRejected() = runBlocking {
        val coordinator = user("coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubId) } returns ClubRole.COORDINATOR

        val duplicateRequest = UpdateClubPermissionsRequest(
            settings = listOf(
                ClubPermissionSetting(ConfigurablePermission.CREATE_PLAYERS, TeamRole.STAFF),
                ClubPermissionSetting(ConfigurablePermission.CREATE_PLAYERS, TeamRole.COACH)
            )
        )

        assertFailsWith<ValidationException> { useCase(coordinator.id, clubId, duplicateRequest) }
        coVerify(exactly = 0) { clubPermissionRepository.replaceOverrides(any(), any()) }
    }
}
