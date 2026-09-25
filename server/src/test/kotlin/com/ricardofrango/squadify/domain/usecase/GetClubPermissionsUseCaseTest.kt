package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class GetClubPermissionsUseCaseTest {

    private val clubPermissionRepository = mockk<ClubPermissionRepository>()
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
    private val useCase = GetClubPermissionsUseCase(clubPermissionRepository, checkUserPermissionUseCase, transactionManager)

    private val clubId = "club-1"

    private fun member() = User("member", "m@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    @Test
    fun returnsOneEntryPerActionFillingGapsWithDefaults() = runBlocking {
        val member = member()
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        // Only one explicit override -- everything else should come back as the action's default.
        coEvery { clubPermissionRepository.getOverrides(clubId) } returns listOf(
            ClubPermissionSetting(ConfigurablePermission.CREATE_PLAYERS, null)
        )

        val response = useCase(member.id, clubId)

        assertEquals(ConfigurablePermission.entries.size, response.settings.size)
        val byAction = response.settings.associateBy { it.action }
        assertEquals(null, byAction.getValue(ConfigurablePermission.CREATE_PLAYERS).minTeamRole)
        assertEquals(TeamRole.COACH, byAction.getValue(ConfigurablePermission.CREATE_OPPONENT).minTeamRole)
        assertEquals(TeamRole.STAFF, byAction.getValue(ConfigurablePermission.MANAGE_LIVE_MATCH).minTeamRole)
    }

    @Test
    fun aPlainMemberCanReadTheSettings() = runBlocking {
        val member = member()
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        coEvery { clubPermissionRepository.getOverrides(clubId) } returns emptyList()

        val response = useCase(member.id, clubId)

        assertEquals(ConfigurablePermission.entries.size, response.settings.size)
    }

    @Test
    fun anOutsiderIsDenied() = runBlocking {
        val outsider = member()
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubId) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(outsider.id, any()) } returns null

        assertFailsWith<ForbiddenException> { useCase(outsider.id, clubId) }
        Unit
    }
}
