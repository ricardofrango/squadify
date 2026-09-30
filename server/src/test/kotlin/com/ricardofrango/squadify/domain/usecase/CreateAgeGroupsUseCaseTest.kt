package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Creating age groups: a club admin/coordinator provides one or more [AgeGroupItemRequest], each with
 * a `startYear`/`endYear` span (single-year groups have startYear == endYear; ranges -- e.g. futsal's
 * Benjamins spanning two birth years -- have startYear > endYear). A range must not be inverted, and
 * must not duplicate an existing age group's exact span in the same season.
 */
class CreateAgeGroupsUseCaseTest {

    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val clubRepository = mockk<ClubRepository>()
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
    private val useCase = CreateAgeGroupsUseCase(
        ageGroupRepository, clubRepository, checkUserPermissionUseCase, transactionManager
    )

    private val clubId = "club-a"
    private val seasonId = "season-1"

    private val club = Club(
        id = clubId, name = "CD Ribamar", status = ClubStatus.ACTIVE,
        createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FUTSAL
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
    fun createsATwoYearRangeAgeGroup() = runBlocking {
        val coord = coordinator()
        coEvery { clubRepository.getClubById(clubId) } returns club
        coEvery { ageGroupRepository.findByClubAndSeason(clubId, seasonId) } returns emptyList()
        coEvery { ageGroupRepository.createAgeGroups(any()) } answers { firstArg() }

        val result = useCase(
            coord.id,
            CreateAgeGroupsRequest(
                clubId = clubId,
                seasonId = seasonId,
                items = listOf(AgeGroupItemRequest(name = "Benjamins", startYear = 2017, endYear = 2016))
            )
        )

        assertEquals(2017, result.single().startYear)
        assertEquals(2016, result.single().endYear)
    }

    @Test
    fun aNonexistentClubIsNotFound() = runBlocking {
        val coord = coordinator()
        coEvery { clubRepository.getClubById(clubId) } returns null

        assertFailsWith<NotFoundException> {
            useCase(
                coord.id,
                CreateAgeGroupsRequest(
                    clubId = clubId,
                    seasonId = seasonId,
                    items = listOf(AgeGroupItemRequest(name = "Benjamins", startYear = 2017, endYear = 2016))
                )
            )
        }
        Unit
    }

    @Test
    fun rejectsAnInvertedRange() = runBlocking {
        val coord = coordinator()
        coEvery { clubRepository.getClubById(clubId) } returns club
        coEvery { ageGroupRepository.findByClubAndSeason(clubId, seasonId) } returns emptyList()

        assertFailsWith<ValidationException> {
            useCase(
                coord.id,
                CreateAgeGroupsRequest(
                    clubId = clubId,
                    seasonId = seasonId,
                    // startYear must be >= endYear; 2016 < 2017 is invalid.
                    items = listOf(AgeGroupItemRequest(name = "Bad range", startYear = 2016, endYear = 2017))
                )
            )
        }
        Unit
    }

    @Test
    fun rejectsADuplicateRangeInTheSameSeason() = runBlocking {
        val coord = coordinator()
        coEvery { clubRepository.getClubById(clubId) } returns club
        coEvery { ageGroupRepository.findByClubAndSeason(clubId, seasonId) } returns listOf(
            AgeGroup(id = "existing", clubId = clubId, name = "Benjamins", seasonId = seasonId, startYear = 2017, endYear = 2016)
        )

        assertFailsWith<ConflictException> {
            useCase(
                coord.id,
                CreateAgeGroupsRequest(
                    clubId = clubId,
                    seasonId = seasonId,
                    items = listOf(AgeGroupItemRequest(name = "Duplicate", startYear = 2017, endYear = 2016))
                )
            )
        }
        Unit
    }

    @Test
    fun rejectsTheSameRangeTwiceInOneRequest() = runBlocking {
        val coord = coordinator()
        coEvery { clubRepository.getClubById(clubId) } returns club
        coEvery { ageGroupRepository.findByClubAndSeason(clubId, seasonId) } returns emptyList()

        assertFailsWith<ConflictException> {
            useCase(
                coord.id,
                CreateAgeGroupsRequest(
                    clubId = clubId,
                    seasonId = seasonId,
                    items = listOf(
                        AgeGroupItemRequest(name = "Benjamins A", startYear = 2017, endYear = 2016),
                        AgeGroupItemRequest(name = "Benjamins B", startYear = 2017, endYear = 2016)
                    )
                )
            )
        }
        coVerify(exactly = 0) { ageGroupRepository.createAgeGroups(any()) }
    }
}
