package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.FcmTokenRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.EmailService
import com.ricardofrango.squadify.domain.service.PushNotificationService
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AddStaffToAgeGroup
import com.ricardofrango.squadify.model.AddStaffToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coVerify
import kotlin.time.Instant
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Minimal first coverage for this previously-untested use case -- see "Not found" is modelled
 * inconsistently for the same resource" in PROCESS_FLOWS.md §11: the age group lookup now throws
 * NotFoundException (was ValidationException) to match every other Age Group lookup in the codebase.
 */
class AddStaffToAgeGroupUseCaseTest {

    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val fcmTokenRepository = mockk<FcmTokenRepository>(relaxed = true)
    private val emailService = mockk<EmailService>(relaxed = true)
    private val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = AddStaffToAgeGroupUseCase(
        ageGroupRepository,
        userRepository,
        clubRepository,
        fcmTokenRepository,
        transactionManager,
        checkUserPermissionUseCase,
        emailService,
        pushNotificationService
    )

    @Test
    fun aNonexistentAgeGroupIsNotFound() = runBlocking {
        // The age group lookup happens before the permission check, so no permission stubs are
        // needed here.
        coEvery { ageGroupRepository.getAgeGroupById("no-such-age-group") } returns null

        assertFailsWith<NotFoundException> {
            useCase("anyone", "no-such-age-group", AddStaffToAgeGroupRequest(staff = emptyList()))
        }
        Unit
    }

    @Test
    fun theStaffPushOpensTheAgeGroup() = runBlocking {
        val coordinator = User("coord", "c@test.com", firstName = "C", lastName = "O", status = UserStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0))
        val coach = coordinator.copy(id = "coach", email = "coach@test.com")
        coEvery { ageGroupRepository.getAgeGroupById("ag-1") } returns AgeGroup("ag-1", "club-1", "Sub-12", "s-1", 2014, 2014)
        coEvery { clubRepository.getClubById("club-1") } returns null
        coEvery { userRepository.findById("coord") } returns coordinator
        coEvery { userRepository.findById("coach") } returns coach
        coEvery { userRepository.getUserRoleInClub("coord", "club-1") } returns ClubRole.COORDINATOR
        coEvery { userRepository.getUserRolesInClub(listOf("coach"), "club-1") } returns mapOf("coach" to ClubRole.MEMBER)
        coEvery { ageGroupRepository.addStaffToAgeGroup(any()) } returns true
        coEvery { fcmTokenRepository.getTokensForUsers(listOf("coach")) } returns mapOf("coach" to listOf("token-1"))

        useCase("coord", "ag-1", AddStaffToAgeGroupRequest(listOf(AddStaffToAgeGroup("coach", AgeGroupRole.COACH))))

        coVerify { pushNotificationService.sendAgeGroupStaffAdded(listOf("token-1"), PushLink.ageGroup("club-1", "ag-1"), "Sub-12", any()) }
    }
}
