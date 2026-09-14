package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Minimal first coverage for this previously-untested use case -- see "Not found" is modelled
 * inconsistently for the same resource" in PROCESS_FLOWS.md §11: the team lookup now throws
 * NotFoundException (was ValidationException) to match every other Team lookup in the codebase.
 */
class GetTeamPlayersUseCaseTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val userRepository = mockk<UserRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager
    )
    private val useCase = GetTeamPlayersUseCase(playerRepository, teamRepository, checkUserPermissionUseCase, transactionManager)

    @Test
    fun aNonexistentTeamIsNotFound() = runBlocking {
        // The team lookup happens before the permission check, so no user stubs are needed here.
        coEvery { teamRepository.getTeamById("no-such-team") } returns null

        assertFailsWith<NotFoundException> {
            useCase("anyone", "no-such-team")
        }
        Unit
    }
}
