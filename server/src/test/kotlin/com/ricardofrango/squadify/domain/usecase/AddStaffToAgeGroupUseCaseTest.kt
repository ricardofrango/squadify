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
import com.ricardofrango.squadify.model.AddStaffToAgeGroupRequest
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
}
