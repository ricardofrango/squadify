package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Adding players to an age-group pool: a coordinator or an age-group coach may do it; a player must
 * belong to the club and be old enough (`birthDate.year >= ageGroup.endYear`, playing up is fine,
 * playing down is not); players already in the group are skipped so the call is idempotent.
 */
class AddPlayersToAgeGroupUseCaseTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager
    )
    private val useCase = AddPlayersToAgeGroupUseCase(
        playerRepository, ageGroupRepository, checkUserPermissionUseCase, transactionManager
    )

    private val clubIdA = "club-a"
    private val ageGroupId = "age-group-2015"

    // endYear 2015 => players born 2015 or later (younger) are eligible; born 2014 or earlier is not.
    private val ageGroup = AgeGroup(id = ageGroupId, clubId = clubIdA, name = "Sub 2015", seasonId = "s1", startYear = 2015, endYear = 2015)

    private fun user(id: String) = User(
        id = id, email = "$id@test.com", firstName = "Test", lastName = "User",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun player(id: String, birthYear: Int, clubId: String = clubIdA) = Player(
        id = id, clubId = clubId, firstName = "P", lastName = id,
        birthDate = LocalDate(birthYear, 3, 1), active = true
    )

    private fun request(vararg ids: String) = AddPlayersToAgeGroupRequest(playerIds = ids.toList())

    @Test
    fun coordinatorAddsEligiblePlayers() = runBlocking {
        val coord = user("p3-coordinator")
        coEvery { userRepository.findById(coord.id) } returns coord
        coEvery { userRepository.getUserRoleInClub(coord.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns ageGroup
        coEvery { playerRepository.getPlayersData(any()) } returns listOf(player("a", 2015), player("b", 2016))
        coEvery { playerRepository.getPlayersByAgeGroup(ageGroupId) } returns emptyList()
        val captured = slot<List<AgeGroupPlayer>>()
        coEvery { playerRepository.addPlayersToAgeGroup(capture(captured)) } answers { captured.captured }

        val result = useCase(coord.id, ageGroupId, request("a", "b"))

        assertEquals(setOf("a", "b"), result.map { it.playerId }.toSet())
        Unit
    }

    @Test
    fun ageGroupCoachCanAddEvenAsAPlainClubMember() = runBlocking {
        val coach = user("p5-coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ageGroupId) } returns TeamRole.COACH
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns ageGroup
        coEvery { playerRepository.getPlayersData(any()) } returns listOf(player("a", 2016))
        coEvery { playerRepository.getPlayersByAgeGroup(ageGroupId) } returns emptyList()
        coEvery { playerRepository.addPlayersToAgeGroup(any()) } answers { firstArg() }

        useCase(coach.id, ageGroupId, request("a"))
        Unit
    }

    @Test
    fun plainMemberWithNoAgeGroupRoleIsDenied() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ageGroupId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns ageGroup

        assertFailsWith<ForbiddenException> {
            useCase(member.id, ageGroupId, request("a"))
        }
        Unit
    }

    @Test
    fun aPlayerTooOldForTheAgeGroupIsRejected() = runBlocking {
        val coord = user("p3-coordinator")
        coEvery { userRepository.findById(coord.id) } returns coord
        coEvery { userRepository.getUserRoleInClub(coord.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns ageGroup
        coEvery { playerRepository.getPlayersData(any()) } returns listOf(player("old", 2014))
        coEvery { playerRepository.getPlayersByAgeGroup(ageGroupId) } returns emptyList()

        assertFailsWith<ValidationException> {
            useCase(coord.id, ageGroupId, request("old"))
        }
        Unit
    }

    @Test
    fun playersAlreadyInTheAgeGroupAreSkipped() = runBlocking {
        val coord = user("p3-coordinator")
        coEvery { userRepository.findById(coord.id) } returns coord
        coEvery { userRepository.getUserRoleInClub(coord.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns ageGroup
        coEvery { playerRepository.getPlayersData(any()) } returns listOf(player("a", 2016), player("b", 2016))
        coEvery { playerRepository.getPlayersByAgeGroup(ageGroupId) } returns listOf(player("a", 2016))
        val captured = slot<List<AgeGroupPlayer>>()
        coEvery { playerRepository.addPlayersToAgeGroup(capture(captured)) } answers { captured.captured }

        useCase(coord.id, ageGroupId, request("a", "b"))

        assertEquals(listOf("b"), captured.captured.map { it.playerId })
        Unit
    }

    @Test
    fun aTwoYearRangeAgeGroupAcceptsBothBirthYearsAndRejectsOlderPlayers() = runBlocking {
        // Futsal-style range: startYear 2017, endYear 2016 -- players born 2016, 2017, or even later
        // (playing up) are eligible; a player born 2015 (older than the range) is not.
        val rangeAgeGroupId = "age-group-2016-2017"
        val rangeAgeGroup = AgeGroup(
            id = rangeAgeGroupId, clubId = clubIdA, name = "Benjamins", seasonId = "s1",
            startYear = 2017, endYear = 2016
        )
        val coord = user("p3-coordinator")
        coEvery { userRepository.findById(coord.id) } returns coord
        coEvery { userRepository.getUserRoleInClub(coord.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById(rangeAgeGroupId) } returns rangeAgeGroup
        coEvery { playerRepository.getPlayersData(any()) } returns listOf(
            player("y2016", 2016), player("y2017", 2017), player("y2018", 2018), player("tooOld", 2015)
        )
        coEvery { playerRepository.getPlayersByAgeGroup(rangeAgeGroupId) } returns emptyList()
        val captured = slot<List<AgeGroupPlayer>>()
        coEvery { playerRepository.addPlayersToAgeGroup(capture(captured)) } answers { captured.captured }

        val result = useCase(coord.id, rangeAgeGroupId, request("y2016", "y2017", "y2018"))
        assertEquals(setOf("y2016", "y2017", "y2018"), result.map { it.playerId }.toSet())

        assertFailsWith<ValidationException> {
            useCase(coord.id, rangeAgeGroupId, request("tooOld"))
        }
        Unit
    }

    @Test
    fun aPlayerFromAnotherClubIsRejected() = runBlocking {
        val coord = user("p3-coordinator")
        coEvery { userRepository.findById(coord.id) } returns coord
        coEvery { userRepository.getUserRoleInClub(coord.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns ageGroup
        coEvery { playerRepository.getPlayersData(any()) } returns listOf(player("x", 2016, clubId = "club-b"))
        coEvery { playerRepository.getPlayersByAgeGroup(ageGroupId) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(coord.id, ageGroupId, request("x"))
        }
        Unit
    }

    @Test
    fun aNonexistentAgeGroupIsNotFound() = runBlocking {
        // The age group lookup happens before the permission check, so no user/permission stubs
        // are needed here.
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns null

        assertFailsWith<NotFoundException> {
            useCase("anyone", ageGroupId, request("x"))
        }
        Unit
    }

    @Test
    fun aNonexistentPlayerIsNotFound() = runBlocking {
        val coord = user("p3-coordinator")
        coEvery { userRepository.findById(coord.id) } returns coord
        coEvery { userRepository.getUserRoleInClub(coord.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns ageGroup
        coEvery { playerRepository.getPlayersData(any()) } returns emptyList()
        coEvery { playerRepository.getPlayersByAgeGroup(ageGroupId) } returns emptyList()

        assertFailsWith<NotFoundException> {
            useCase(coord.id, ageGroupId, request("does-not-exist"))
        }
        Unit
    }
}
