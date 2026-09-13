package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.CreateTeamsRequest
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamItemRequest
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
 * Batch team creation shares CreateTeamUseCase's rules (COORDINATOR+ gate; the age group must
 * belong to the request's club and season), applied once for the whole batch, plus a blank-name
 * check on every row. Mirrors CreateTeamUseCaseTest.
 */
class CreateTeamsUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val clubRepository = mockk<com.ricardofrango.squadify.domain.repository.ClubRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager)
    private val useCase = CreateTeamsUseCase(teamRepository, ageGroupRepository, clubRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"

    init {
        coEvery { clubRepository.getClubById(clubIdA) } returns Club(
            id = clubIdA, name = "C", status = ClubStatus.ACTIVE,
            createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL
        )
    }

    private val consistentAgeGroup = AgeGroup(
        id = "age-group-1",
        clubId = clubIdA,
        name = "U12",
        seasonId = "season-1",
        birthYear = 2014
    )

    private fun user(id: String) = User(
        id = id,
        email = "$id@test.com",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        isPlatformAdmin = false,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun request(vararg names: String) = CreateTeamsRequest(
        clubId = clubIdA,
        seasonId = "season-1",
        ageGroupId = "age-group-1",
        items = names.map { TeamItemRequest(name = it) }
    )

    @Test
    fun coordinatorCreatesEveryTeamInTheBatch() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        val created = slot<List<Team>>()
        coEvery { teamRepository.createTeams(capture(created)) } answers { created.captured }

        val result = useCase(coordinator.id, request("Team A", "Team B", "Team C"))

        assertEquals(listOf("Team A", "Team B", "Team C"), result.map { it.name })
        assertEquals(3, created.captured.size)
        Unit
    }

    @Test
    fun plainMemberCannotCreateTeams() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER

        assertFailsWith<ForbiddenException> {
            useCase(member.id, request("Team A"))
        }
        Unit
    }

    @Test
    fun aBlankTeamNameInTheBatchIsRejected() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup

        assertFailsWith<ValidationException> {
            useCase(admin.id, request("Team A", "  "))
        }
        Unit
    }

    @Test
    fun anEmptyBatchIsRejected() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN

        assertFailsWith<ValidationException> {
            useCase(admin.id, request())
        }
        Unit
    }

    @Test
    fun ageGroupFromADifferentSeasonIsRejected() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup.copy(
            seasonId = "season-X" // inconsistent with the request's season-1
        )

        assertFailsWith<ValidationException> {
            useCase(admin.id, request("Team A"))
        }
        Unit
    }
}
