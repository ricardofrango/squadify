package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.ClubAccessPolicy
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadify.domain.repository.ClubRepository
import com.sportssoftw.squadify.domain.repository.InviteRepository
import com.sportssoftw.squadify.domain.repository.PlayerRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.ForbiddenException
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.model.AgeGroup
import com.sportssoftw.squadify.model.Player
import com.sportssoftw.squadify.model.AgeGroupRole
import com.sportssoftw.squadify.model.UpdatePlayerRequest
import com.sportssoftw.squadify.model.User
import com.sportssoftw.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * First coverage for this previously-untested use case -- focused on the age-group-scoped coach path
 * added alongside the pre-existing club-wide coordinator gate (see ConfigurablePermission.EDIT_PLAYER,
 * TECHNICAL_DECISIONS.md), mirroring CreatePlayersUseCaseTest's shape.
 */
class UpdatePlayerUseCaseTest {

    private val playerRepository = mockk<PlayerRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val inviteRepository = mockk<InviteRepository>(relaxed = true)
    private val clubRepository = mockk<ClubRepository>(relaxed = true)
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = UpdatePlayerUseCase(
        playerRepository, ageGroupRepository, transactionManager, ClubAccessPolicy(checkUserPermissionUseCase),
        ClubPlayerContacts(playerRepository, userRepository, inviteRepository, clubRepository)
    )

    private val clubId = "club-1"
    private val otherClubId = "club-2"
    private val ag1 = "ag-1"
    private val playerId = "player-1"

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    private fun existingPlayer() = Player(
        id = playerId, clubId = clubId, firstName = "First", lastName = "Last", birthDate = LocalDate(2012, 1, 1), active = true
    )

    @Test
    fun anAgeGroupCoachCanEditAPlayerInTheirOwnAgeGroup() = runBlocking {
        val coach = coach()
        coEvery { playerRepository.getPlayerById(playerId) } returns existingPlayer()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "U12", "s-1", 2013, 2013)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { playerRepository.updatePlayer(any()) } returns true

        val result = useCase(coach.id, playerId, UpdatePlayerRequest(firstName = "New Name", ageGroupId = ag1))

        assertTrue(result)
    }

    @Test
    fun aCoachOfADifferentAgeGroupIsDenied() = runBlocking {
        val coach = coach()
        coEvery { playerRepository.getPlayerById(playerId) } returns existingPlayer()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "U12", "s-1", 2013, 2013)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns null

        assertFailsWith<ForbiddenException> {
            useCase(coach.id, playerId, UpdatePlayerRequest(firstName = "New Name", ageGroupId = ag1))
        }
        Unit
    }

    @Test
    fun aCoachCannotEditAPlayerClubWideWithoutAnAgeGroupContext() = runBlocking {
        val coach = coach()
        coEvery { playerRepository.getPlayerById(playerId) } returns existingPlayer()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null

        assertFailsWith<ForbiddenException> {
            useCase(coach.id, playerId, UpdatePlayerRequest(firstName = "New Name"))
        }
        Unit
    }

    @Test
    fun anAgeGroupFromAnotherClubIsRejectedEvenIfTheCallerCoachesIt() = runBlocking {
        val coach = coach()
        coEvery { playerRepository.getPlayerById(playerId) } returns existingPlayer()
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, otherClubId, "U12", "s-1", 2013, 2013)

        assertFailsWith<ForbiddenException> {
            useCase(coach.id, playerId, UpdatePlayerRequest(firstName = "New Name", ageGroupId = ag1))
        }
        Unit
    }

    @Test
    fun aNonExistentAgeGroupIsRejected() = runBlocking {
        val coach = coach()
        coEvery { playerRepository.getPlayerById(playerId) } returns existingPlayer()
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns null

        assertFailsWith<NotFoundException> {
            useCase(coach.id, playerId, UpdatePlayerRequest(firstName = "New Name", ageGroupId = ag1))
        }
        Unit
    }
}
