package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroup
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

/** Taking someone off an age group: admins and coordinators only, and they stay in the club. */
class RemoveStaffFromAgeGroupUseCaseTest {
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init {
        coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null
        coEvery { ageGroupRepository.getAgeGroupById("ag-1") } returns AgeGroup("ag-1", "club-1", "Sub-12", "s-1", 2014, 2014)
        coEvery { ageGroupRepository.getAgeGroupById("missing") } returns null
        for ((id, role) in listOf("coord" to ClubRole.COORDINATOR, "member" to ClubRole.MEMBER)) {
            coEvery { userRepository.findById(id) } returns User(id, "$id@test.com", firstName = "A", lastName = "B", status = UserStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0))
            coEvery { userRepository.getUserRoleInClub(id, "club-1") } returns role
        }
    }
    private val useCase = RemoveStaffFromAgeGroupUseCase(
        ageGroupRepository,
        transactionManager,
        CheckUserPermissionUseCase(userRepository, mockk<TeamRepository>(), mockk<PlayerRepository>(), ageGroupRepository, transactionManager, clubPermissionRepository)
    )

    @Test
    fun aCoordinatorTakesThemOffTheAgeGroupOnly() = runBlocking {
        coEvery { ageGroupRepository.removeStaffFromAgeGroup("ag-1", "coach") } returns true

        useCase("coord", "ag-1", "coach")

        coVerify { ageGroupRepository.removeStaffFromAgeGroup("ag-1", "coach") }
        coVerify(exactly = 0) { ageGroupRepository.removeStaffFromClub(any(), any()) }
    }

    @Test
    fun aMemberCant() = runBlocking {
        assertFailsWith<ForbiddenException> { useCase("member", "ag-1", "coach") }
        coVerify(exactly = 0) { ageGroupRepository.removeStaffFromAgeGroup(any(), any()) }
    }

    @Test
    fun someoneNotOnTheAgeGroupOrAMissingAgeGroupIsNotFound() = runBlocking {
        coEvery { ageGroupRepository.removeStaffFromAgeGroup("ag-1", "nobody") } returns false
        assertFailsWith<NotFoundException> { useCase("coord", "ag-1", "nobody") }
        assertFailsWith<NotFoundException> { useCase("coord", "missing", "coach") }
        Unit
    }
}
