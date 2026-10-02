package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SportType
import kotlinx.datetime.LocalDate
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateAgeGroupRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Updating an age group's name and/or year span. A range must never end up inverted
 * (startYear < endYear), whether that comes from the request or is inherited from the existing row.
 */
class UpdateAgeGroupUseCaseTest {

    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val clubRepository = mockk<ClubRepository>()
    private val useCase = UpdateAgeGroupUseCase(ageGroupRepository, checkUserPermissionUseCase, transactionManager, clubRepository, playerRepository)

    private val clubId = "club-a"
    private val ageGroupId = "age-group-1"
    private val existing = AgeGroup(
        id = ageGroupId, clubId = clubId, name = "Sub 10", seasonId = "s1", startYear = 2017, endYear = 2016
    )

    private fun coordinator(): User {
        val user = User(
            id = "coord", email = "coord@test.com", firstName = "Coord", lastName = "User",
            status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
        )
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { userRepository.getUserRoleInClub(user.id, clubId) } returns ClubRole.COORDINATOR
        return user
    }

    @Test
    fun updatesTheYearRange() = runBlocking {
        val coord = coordinator()
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns existing
        coEvery { ageGroupRepository.findByClubAndSeason(clubId, "s1") } returns listOf(existing)
        coEvery { playerRepository.getPlayersByAgeGroup(ageGroupId) } returns listOf(player(2017))
        val captured = slot<AgeGroup>()
        coEvery { ageGroupRepository.updateAgeGroup(capture(captured)) } returns true

        useCase(coord.id, ageGroupId, UpdateAgeGroupRequest(startYear = 2018, endYear = 2017))

        assertEquals(2018, captured.captured.startYear)
        assertEquals(2017, captured.captured.endYear)
    }

    @Test
    fun rejectsAnUpdateThatWouldInvertTheRange() = runBlocking {
        val coord = coordinator()
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns existing

        assertFailsWith<ValidationException> {
            // Only endYear changes, but 2019 > existing startYear (2017) inverts the range.
            useCase(coord.id, ageGroupId, UpdateAgeGroupRequest(endYear = 2019))
        }
        Unit
    }

    private fun player(birthYear: Int) = Player(
        id = "p$birthYear", clubId = clubId, firstName = "P", lastName = "$birthYear", birthDate = LocalDate(birthYear, 3, 1), active = true
    )

    private fun setUp(pool: List<Player> = emptyList(), others: List<AgeGroup> = emptyList()): String {
        val coord = coordinator()
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns existing
        coEvery { ageGroupRepository.findByClubAndSeason(clubId, "s1") } returns listOf(existing) + others
        coEvery { playerRepository.getPlayersByAgeGroup(ageGroupId) } returns pool
        coEvery { ageGroupRepository.updateAgeGroup(any()) } returns true
        coEvery { clubRepository.getClubById(clubId) } returns Club(id = clubId, name = "FC", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)
        return coord.id
    }

    @Test
    fun rejectsASpanAnotherAgeGroupInTheSeasonAlreadyHas() = runBlocking {
        val other = existing.copy(id = "age-group-2", name = "Sub 11", startYear = 2015, endYear = 2015)
        val coord = setUp(others = listOf(other))
        assertFailsWith<ConflictException> { useCase(coord, ageGroupId, UpdateAgeGroupRequest(startYear = 2015, endYear = 2015)) }
        Unit
    }

    @Test
    fun rejectsASpanThatWouldShutOutPlayersAlreadyInThePool() = runBlocking {
        // Born 2016: eligible for 2017-2016, not for a single-year 2017 group.
        val coord = setUp(pool = listOf(player(2016), player(2017)))
        assertFailsWith<ConflictException> { useCase(coord, ageGroupId, UpdateAgeGroupRequest(startYear = 2017, endYear = 2017)) }
        Unit
    }

    @Test
    fun wideningTheSpanNeverShutsAnyoneOut() = runBlocking {
        val coord = setUp(pool = listOf(player(2016), player(2017)))
        useCase(coord, ageGroupId, UpdateAgeGroupRequest(startYear = 2017, endYear = 2015))
        coVerify { ageGroupRepository.updateAgeGroup(match { it.endYear == 2015 }) }
    }

    @Test
    fun savesAValidTeamSizeAndRejectsOneTheSportDoesNotHave() = runBlocking {
        val coord = setUp()
        useCase(coord, ageGroupId, UpdateAgeGroupRequest(teamSize = 7))
        coVerify { ageGroupRepository.updateAgeGroup(match { it.teamSize == 7 }) }
        assertFailsWith<ValidationException> { useCase(coord, ageGroupId, UpdateAgeGroupRequest(teamSize = 6)) }
        Unit
    }

    @Test
    fun rejectsABlankName() = runBlocking {
        val coord = setUp()
        assertFailsWith<ValidationException> { useCase(coord, ageGroupId, UpdateAgeGroupRequest(name = " ")) }
        Unit
    }
}
