package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UpdateOpponentRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * First coverage for this previously-untested (and, as of this change, still client-unreachable -- no
 * screen calls it yet) use case -- focused on the age-group-scoped coach path added alongside the
 * pre-existing club-wide coordinator gate (see ConfigurablePermission.EDIT_OPPONENT), mirroring
 * CreateOpponentUseCaseTest's shape.
 */
class UpdateOpponentUseCaseTest {

    private val opponentRepository = mockk<OpponentRepository>(relaxed = true)
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = UpdateOpponentUseCase(opponentRepository, ageGroupRepository, checkUserPermissionUseCase, transactionManager)

    private val clubId = "club-1"
    private val otherClubId = "club-2"
    private val ag1 = "ag-1"
    private val opponentId = "opp-1"

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    private fun existingOpponent() = Opponent(id = opponentId, clubId = clubId, name = "Rivals FC")

    @Test
    fun anAgeGroupCoachCanEditAnOpponentForTheirOwnAgeGroup() = runBlocking {
        val coach = coach()
        coEvery { opponentRepository.getOpponentById(opponentId) } returns existingOpponent()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "U12", "s-1", 2013, 2013)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
        coEvery { opponentRepository.updateOpponent(any()) } returns true

        val result = useCase(coach.id, opponentId, UpdateOpponentRequest(name = "New Name", ageGroupId = ag1))

        assertTrue(result)
    }

    @Test
    fun aCoachOfADifferentAgeGroupIsDenied() = runBlocking {
        val coach = coach()
        coEvery { opponentRepository.getOpponentById(opponentId) } returns existingOpponent()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "U12", "s-1", 2013, 2013)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns null

        assertFailsWith<ForbiddenException> {
            useCase(coach.id, opponentId, UpdateOpponentRequest(name = "New Name", ageGroupId = ag1))
        }
        Unit
    }

    @Test
    fun anAgeGroupFromAnotherClubIsRejectedEvenIfTheCallerCoachesIt() = runBlocking {
        val coach = coach()
        coEvery { opponentRepository.getOpponentById(opponentId) } returns existingOpponent()
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, otherClubId, "U12", "s-1", 2013, 2013)

        assertFailsWith<ForbiddenException> {
            useCase(coach.id, opponentId, UpdateOpponentRequest(name = "New Name", ageGroupId = ag1))
        }
        Unit
    }

    @Test
    fun aNonExistentAgeGroupIsRejected() = runBlocking {
        val coach = coach()
        coEvery { opponentRepository.getOpponentById(opponentId) } returns existingOpponent()
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns null

        assertFailsWith<NotFoundException> {
            useCase(coach.id, opponentId, UpdateOpponentRequest(name = "New Name", ageGroupId = ag1))
        }
        Unit
    }
}
