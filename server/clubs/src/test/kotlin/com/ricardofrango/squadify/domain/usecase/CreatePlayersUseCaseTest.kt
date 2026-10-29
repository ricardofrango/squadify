package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Minimal first coverage for this previously-untested use case -- focused on the age-group-scoped
 * coach path added alongside the club-wide coordinator gate (see PERMISSION_TEST_MATRIX.md).
 */
class CreatePlayersUseCaseTest {

    private val playerRepository = mockk<PlayerRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
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
    private val useCase = CreatePlayersUseCase(
        playerRepository, userRepository, inviteRepository, clubRepository, ageGroupRepository, transactionManager, checkUserPermissionUseCase
    )

    private val clubId = "club-1"
    private val ag1 = "ag-1"

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    private fun request(ageGroupId: String? = null) = CreatePlayersRequest(
        clubId = clubId,
        players = listOf(CreatePlayer(firstName = "First", lastName = "Last", birthDate = LocalDate(2012, 1, 1))),
        ageGroupId = ageGroupId
    )

    @Test
    fun anAgeGroupCoachWithNoClubRoleCanCreatePlayersForTheirOwnAgeGroup() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "U12", "s-1", 2013, 2013)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { userRepository.findByEmails(emptyList()) } returns emptyList()
        coEvery { playerRepository.createPlayers(any()) } answers { firstArg() }

        val result = useCase(coach.id, request(ageGroupId = ag1))

        kotlin.test.assertEquals(1, result.size)
    }

    @Test
    fun aCoachOfADifferentAgeGroupIsDenied() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "U12", "s-1", 2013, 2013)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns null

        assertFailsWith<ForbiddenException> { useCase(coach.id, request(ageGroupId = ag1)) }
        Unit
    }

    @Test
    fun aCoachCannotCreatePlayersClubWideWithoutAnAgeGroupContext() = runBlocking {
        // No ageGroupId supplied -- the club-wide/standalone entry point stays coordinator-only, even
        // for someone who coaches an age group somewhere in this club.
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null

        assertFailsWith<ForbiddenException> { useCase(coach.id, request(ageGroupId = null)) }
        Unit
    }

    @Test
    fun anAgeGroupFromAnotherClubIsRejected() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, "other-club", "U12", "s-1", 2013, 2013)

        assertFailsWith<ForbiddenException> { useCase(coach.id, request(ageGroupId = ag1)) }
        Unit
    }

    @Test
    fun aNonexistentAgeGroupIsNotFound() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { ageGroupRepository.getAgeGroupById("no-such-ag") } returns null

        assertFailsWith<NotFoundException> { useCase(coach.id, request(ageGroupId = "no-such-ag")) }
        Unit
    }

    @Test
    fun coordinatorCanCreatePlayersClubWide() = runBlocking {
        val coordinator = User("p2-coordinator", "c@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubId) } returns ClubRole.COORDINATOR
        coEvery { userRepository.findByEmails(emptyList()) } returns emptyList()
        coEvery { playerRepository.createPlayers(any()) } answers { firstArg() }

        val result = useCase(coordinator.id, request(ageGroupId = null))

        kotlin.test.assertEquals(1, result.size)
        coVerify(exactly = 0) { ageGroupRepository.getAgeGroupById(any()) }
    }
}
