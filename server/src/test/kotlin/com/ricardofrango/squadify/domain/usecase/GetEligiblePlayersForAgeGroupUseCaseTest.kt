package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
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
class GetEligiblePlayersForAgeGroupUseCaseTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetEligiblePlayersForAgeGroupUseCase(
        playerRepository, ageGroupRepository, checkUserPermissionUseCase, transactionManager
    )

    @Test
    fun aNonexistentAgeGroupIsNotFound() = runBlocking {
        // The age group lookup happens before the permission check, so no user stubs are needed here.
        coEvery { ageGroupRepository.getAgeGroupById("no-such-age-group") } returns null

        assertFailsWith<NotFoundException> {
            useCase("anyone", "no-such-age-group")
        }
        Unit
    }
}
