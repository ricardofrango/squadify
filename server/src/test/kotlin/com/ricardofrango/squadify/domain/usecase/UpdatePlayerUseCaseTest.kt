package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UpdatePlayerRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
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
        playerRepository, userRepository, inviteRepository, clubRepository, ageGroupRepository, transactionManager, checkUserPermissionUseCase
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
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
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
