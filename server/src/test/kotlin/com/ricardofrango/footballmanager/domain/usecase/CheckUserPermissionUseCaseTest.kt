package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class CheckUserPermissionUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val useCase = CheckUserPermissionUseCase(userRepository, teamRepository)

    private val userId = "user-1"
    private val clubId = "club-1"
    private val teamId = "team-1"

    private val regularUser = User(
        id = userId,
        email = "user@test.com",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        isPlatformAdmin = false,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    private val platformAdmin = regularUser.copy(isPlatformAdmin = true)

    @Before
    fun setup() {
        coEvery { userRepository.findById(userId) } returns regularUser
    }

    @Test
    fun shouldAllowPlatformAdminRegardlessOfRole() {
        runBlocking {
            coEvery { userRepository.findById(userId) } returns platformAdmin

            val result = useCase(userId, clubId, Role.CLUB_ADMIN)

            assertEquals(Role.CLUB_ADMIN, result)
        }
    }

    @Test
    fun shouldThrowForbiddenExceptionIfUserNotFound() {
        runBlocking {
            coEvery { userRepository.findById(userId) } returns null

            assertFailsWith<ForbiddenException> {
                useCase(userId, clubId, Role.PLAYER)
            }
        }
    }

    @Test
    fun shouldThrowForbiddenExceptionIfUserHasNoRoleInClub() {
        runBlocking {
            coEvery { userRepository.getUserRoleInClub(userId, clubId) } returns null

            assertFailsWith<ForbiddenException> {
                useCase(userId, clubId, Role.PLAYER)
            }
        }
    }

    @Test
    fun shouldThrowForbiddenExceptionIfUserRoleLevelIsTooLow() {
        runBlocking {
            coEvery { userRepository.getUserRoleInClub(userId, clubId) } returns Role.COACH // Level 3

            assertFailsWith<ForbiddenException> {
                useCase(userId, clubId, Role.COORDINATOR) // Requires Level 4
            }
        }
    }

    @Test
    fun shouldAllowIfUserRoleLevelIsEqualToMinRole() {
        runBlocking {
            coEvery { userRepository.getUserRoleInClub(userId, clubId) } returns Role.COACH

            val result = useCase(userId, clubId, Role.COACH)

            assertEquals(Role.COACH, result)
        }
    }

    @Test
    fun shouldAllowIfUserRoleLevelIsHigherThanMinRole() {
        runBlocking {
            coEvery { userRepository.getUserRoleInClub(userId, clubId) } returns Role.CLUB_ADMIN

            val result = useCase(userId, clubId, Role.COACH)

            assertEquals(Role.CLUB_ADMIN, result)
        }
    }

    @Test
    fun shouldAllowCoordinatorToAccessAnyTeam() {
        runBlocking {
            coEvery { userRepository.getUserRoleInClub(userId, clubId) } returns Role.COORDINATOR

            val result = useCase(userId, clubId, Role.COACH, teamId = "some-other-team")

            assertEquals(Role.COORDINATOR, result)
        }
    }

    @Test
    fun shouldAllowCoachToAccessTheirAssignedTeam() {
        runBlocking {
            coEvery { userRepository.getUserRoleInClub(userId, clubId) } returns Role.COACH
            coEvery { teamRepository.isUserStaffOfTeam(userId, teamId) } returns true

            val result = useCase(userId, clubId, Role.COACH, teamId = teamId)

            assertEquals(Role.COACH, result)
        }
    }

    @Test
    fun shouldThrowForbiddenExceptionIfCoachTriesToAccessOtherTeam() {
        runBlocking {
            coEvery { userRepository.getUserRoleInClub(userId, clubId) } returns Role.COACH
            coEvery { teamRepository.isUserStaffOfTeam(userId, teamId) } returns false

            assertFailsWith<ForbiddenException> {
                useCase(userId, clubId, Role.COACH, teamId = teamId)
            }
        }
    }
}
