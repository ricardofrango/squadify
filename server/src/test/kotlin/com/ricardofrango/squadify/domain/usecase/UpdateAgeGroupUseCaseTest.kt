package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateAgeGroupRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Updating an age group's name and/or year span. A range must never end up inverted
 * (startYear < endYear), whether that comes from the request or is inherited from the existing row.
 */
class UpdateAgeGroupUseCaseTest {

    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager
    )
    private val useCase = UpdateAgeGroupUseCase(ageGroupRepository, checkUserPermissionUseCase, transactionManager)

    private val clubId = "club-a"
    private val ageGroupId = "age-group-1"
    private val existing = AgeGroup(
        id = ageGroupId, clubId = clubId, name = "Sub 10", seasonId = "s1", startYear = 2017, endYear = 2016
    )

    private fun coordinator(): User {
        val user = User(
            id = "coord", email = "coord@test.com", firstName = "Coord", lastName = "User",
            status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
        )
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { userRepository.getUserRoleInClub(user.id, clubId) } returns ClubRole.COORDINATOR
        return user
    }

    @Test
    fun updatesTheYearRange() = runBlocking {
        val coord = coordinator()
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns existing
        val captured = slot<AgeGroup>()
        coEvery { ageGroupRepository.updateAgeGroup(capture(captured)) } returns true

        useCase(coord.id, ageGroupId, UpdateAgeGroupRequest(startYear = 2018, endYear = 2017))

        assertEquals(2018, captured.captured.startYear)
        assertEquals(2017, captured.captured.endYear)
    }

    @Test
    fun rejectsAnUpdateThatWouldInvertTheRange() = runBlocking {
        val coord = coordinator()
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns existing

        assertFailsWith<ValidationException> {
            // Only endYear changes, but 2019 > existing startYear (2017) inverts the range.
            useCase(coord.id, ageGroupId, UpdateAgeGroupRequest(endYear = 2019))
        }
        Unit
    }
}
