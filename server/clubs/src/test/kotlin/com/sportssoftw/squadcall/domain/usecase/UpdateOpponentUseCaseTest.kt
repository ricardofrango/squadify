package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.ClubAccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.model.Opponent
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.UpdateOpponentRequest
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import com.sportssoftw.squadcall.exception.ConflictException
import com.sportssoftw.squadcall.model.ClubRole
import io.mockk.slot
import kotlin.test.assertNull
import kotlin.test.assertEquals
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The age-group-scoped coach path added alongside the club-wide coordinator gate (see
 * ConfigurablePermission.EDIT_OPPONENT), mirroring CreateOpponentUseCaseTest's shape, and the edit
 * rules of Edit opponent (design 38e): a unique name, null keeps a field, blank clears it.
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
    private val useCase = UpdateOpponentUseCase(opponentRepository, ageGroupRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager)

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
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
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

    private fun asCoordinator(): String {
        val coordinator = User("coordinator", "c@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubId) } returns ClubRole.COORDINATOR
        return coordinator.id
    }

    @Test
    fun renamingToAnotherOpponentsNameIsAConflict() = runBlocking<Unit> {
        val userId = asCoordinator()
        coEvery { opponentRepository.getOpponentById(opponentId) } returns existingOpponent()
        coEvery { opponentRepository.getOpponentsByClub(clubId) } returns listOf(existingOpponent(), Opponent("opp-2", clubId, "CD Mafra"))

        assertFailsWith<ConflictException> { useCase(userId, opponentId, UpdateOpponentRequest(name = "cd mafra")) }
    }

    @Test
    fun changingOnlyTheCaseOfItsOwnNameIsAllowed() = runBlocking<Unit> {
        val userId = asCoordinator()
        coEvery { opponentRepository.getOpponentById(opponentId) } returns existingOpponent()
        val saved = slot<Opponent>()
        coEvery { opponentRepository.updateOpponent(capture(saved)) } returns true

        useCase(userId, opponentId, UpdateOpponentRequest(name = "RIVALS FC"))

        assertEquals("RIVALS FC", saved.captured.name)
    }

    @Test
    fun nullKeepsAFieldAndBlankClearsIt() = runBlocking<Unit> {
        val userId = asCoordinator()
        coEvery { opponentRepository.getOpponentById(opponentId) } returns existingOpponent().copy(
            stadiumName = "Campo", stadiumAddress = "Rua 1", notes = "Parking", shortName = "RFC"
        )
        val saved = slot<Opponent>()
        coEvery { opponentRepository.updateOpponent(capture(saved)) } returns true

        useCase(userId, opponentId, UpdateOpponentRequest(stadiumAddress = " ", notes = "Artificial pitch", shortName = "rfc2"))

        assertEquals("Rivals FC", saved.captured.name)
        assertEquals("Campo", saved.captured.stadiumName)
        assertNull(saved.captured.stadiumAddress)
        assertEquals("Artificial pitch", saved.captured.notes)
        assertEquals("RFC2", saved.captured.shortName)
    }
}
