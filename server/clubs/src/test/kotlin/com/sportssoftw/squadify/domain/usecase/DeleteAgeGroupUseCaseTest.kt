package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.ClubAccessPolicy
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadify.domain.repository.DeletedAgeGroup
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.domain.service.ClubMatchesBus
import com.sportssoftw.squadify.domain.service.ImageStorage
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.ForbiddenException
import com.sportssoftw.squadify.model.AgeGroup
import com.sportssoftw.squadify.model.AgeGroupRole
import com.sportssoftw.squadify.model.ClubRole
import com.sportssoftw.squadify.model.User
import com.sportssoftw.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

// Deleting an age group on the clubs server: coordinators and admins, not its coaches; the players stay
// on the club's roster.
class DeleteAgeGroupUseCaseTest {
    private val ageGroupRepository = mockk<AgeGroupRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val policy = ClubAccessPolicy(
        CheckUserPermissionUseCase(userRepository, mockk<TeamRepository>(), mockk<PlayerRepository>(), ageGroupRepository, transactionManager, clubPermissionRepository)
    )
    private val useCase = DeleteAgeGroupUseCase(ageGroupRepository, policy, transactionManager, mockk<ImageStorage>(relaxed = true), mockk<ClubMatchesBus>(relaxed = true))

    private val ageGroup = AgeGroup("ag-1", "club-1", "Sub 11", "s-1", 2016, 2016)
    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    init {
        coEvery { ageGroupRepository.getAgeGroupById("ag-1") } returns ageGroup
        coEvery { ageGroupRepository.deleteAgeGroup("ag-1", any()) } returns DeletedAgeGroup(listOf("m-1", "m-2"), null, emptyList())
    }

    @Test
    fun aCoordinatorDeletesItAndThePlayersStay() = runBlocking {
        coEvery { userRepository.findById("coord") } returns user("coord")
        coEvery { userRepository.getUserRoleInClub("coord", "club-1") } returns ClubRole.COORDINATOR

        assertEquals(2, useCase("coord", "ag-1"))
        coVerify { ageGroupRepository.deleteAgeGroup("ag-1", deleteLonePlayers = false) }
    }

    @Test
    fun itsCoachMayNot() = runBlocking {
        coEvery { userRepository.findById("coach") } returns user("coach")
        coEvery { userRepository.getUserRoleInClub("coach", "club-1") } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup("coach", "ag-1") } returns AgeGroupRole.COACH

        assertFailsWith<ForbiddenException> { useCase("coach", "ag-1") }
        coVerify(exactly = 0) { ageGroupRepository.deleteAgeGroup(any(), any()) }
    }
}
