package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.ClubAccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ConflictException
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.CreateOpponentRequest
import com.sportssoftw.squadcall.model.Opponent
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class CreateOpponentUseCaseTest {

    private val opponentRepository = mockk<OpponentRepository>()
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
    private val useCase = CreateOpponentUseCase(opponentRepository, ageGroupRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager)

    private val clubId = "club-1"
    private val ag1 = "ag-1"
    private val request = CreateOpponentRequest(name = "Rivals FC")

    init { coEvery { opponentRepository.getOpponentsByClub(clubId) } returns listOf(Opponent("o1", clubId, "AD Fontelas")) }

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    @Test
    fun anAgeGroupCoachWithNoClubRoleCanCreateAnOpponentForTheirOwnAgeGroup() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "U12", "s-1", 2013, 2013)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { opponentRepository.createOpponent(any()) } answers { firstArg() }

        val result = useCase(coach.id, clubId, request.copy(ageGroupId = ag1))

        assertEquals("Rivals FC", result.name)
    }

    @Test
    fun aCoachIsDeniedForAnAgeGroupTheyDontBelongTo() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "U12", "s-1", 2013, 2013)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns null

        assertFailsWith<ForbiddenException> { useCase(coach.id, clubId, request.copy(ageGroupId = ag1)) }
        Unit
    }

    @Test
    fun aCoachCannotCreateAnOpponentClubWideWithoutAnAgeGroupContext() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null

        assertFailsWith<ForbiddenException> { useCase(coach.id, clubId, request) }
        Unit
    }

    @Test
    fun anAgeGroupFromAnotherClubIsRejected() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, "other-club", "U12", "s-1", 2013, 2013)

        assertFailsWith<ForbiddenException> { useCase(coach.id, clubId, request.copy(ageGroupId = ag1)) }
        Unit
    }

    @Test
    fun aNonexistentAgeGroupIsNotFound() = runBlocking {
        val coach = coach()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { ageGroupRepository.getAgeGroupById("no-such-ag") } returns null

        assertFailsWith<NotFoundException> { useCase(coach.id, clubId, request.copy(ageGroupId = "no-such-ag")) }
        Unit
    }

    @Test
    fun coordinatorCanCreateAnOpponentClubWide() = runBlocking {
        val coordinator = User("p2-coordinator", "c@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubId) } returns ClubRole.COORDINATOR
        coEvery { opponentRepository.createOpponent(any()) } answers { firstArg() }

        val result = useCase(coordinator.id, clubId, request)

        assertEquals("Rivals FC", result.name)
    }

    private fun asCoordinator(): String {
        val coordinator = User("p2-coordinator", "c@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubId) } returns ClubRole.COORDINATOR
        coEvery { opponentRepository.createOpponent(any()) } answers { firstArg() }
        return coordinator.id
    }

    @Test
    fun aNameTheClubAlreadyHasIsAConflictIgnoringCase() = runBlocking<Unit> {
        val userId = asCoordinator()
        assertFailsWith<ConflictException> { useCase(userId, clubId, CreateOpponentRequest(name = "  ad fontelas ")) }
    }

    @Test
    fun aBlankNameIsRejected() = runBlocking<Unit> {
        val userId = asCoordinator()
        assertFailsWith<ValidationException> { useCase(userId, clubId, CreateOpponentRequest(name = "   ")) }
    }

    @Test
    fun theShortNameIsUppercasedAndCappedAtFiveCharacters() = runBlocking<Unit> {
        val userId = asCoordinator()
        assertEquals("GDS", useCase(userId, clubId, CreateOpponentRequest(name = "GD Sobreirense", shortName = " gds ")).shortName)
        assertFailsWith<ValidationException> { useCase(userId, clubId, CreateOpponentRequest(name = "Sporting Lourel", shortName = "SPORTL")) }
    }

    @Test
    fun theDetailsAreTrimmedAndBlankOnesLeftEmpty() = runBlocking<Unit> {
        val userId = asCoordinator()
        val result = useCase(
            userId, clubId,
            CreateOpponentRequest(
                name = " Sporting Lourel ",
                stadiumName = " Campo do Lourel ",
                stadiumAddress = "  ",
                contactEmail = "geral@lourel.pt",
                notes = ""
            )
        )
        assertEquals("Sporting Lourel", result.name)
        assertEquals("Campo do Lourel", result.stadiumName)
        assertEquals(null, result.stadiumAddress)
        assertEquals("geral@lourel.pt", result.contactEmail)
        assertEquals(null, result.notes)
    }
}
