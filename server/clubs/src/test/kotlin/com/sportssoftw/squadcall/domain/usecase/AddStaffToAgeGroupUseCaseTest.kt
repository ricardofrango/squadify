package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.FcmTokenRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.service.EmailService
import com.sportssoftw.squadcall.domain.service.PushNotificationService
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.AddStaffToAgeGroup
import com.sportssoftw.squadcall.model.AddStaffToAgeGroupRequest
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.PushLink
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
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
