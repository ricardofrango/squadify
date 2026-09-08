package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ConflictException
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.CreateSeasonRequest
import com.ricardofrango.footballmanager.model.Season
import com.ricardofrango.footballmanager.model.SeasonType
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Covers CreateSeasonUseCase's own business rules -- permission, the start/end date ordering added
 * here, and the existing duplicate-season conflict -- against `PERMISSION_TEST_MATRIX.md`-style
 * personas. Season creation UI-side (the date pickers) also keeps the invalid case from happening in
 * normal use (see `DatePickerSheet`'s `minDate`/`maxDate`), but the server can't trust the client alone.
 */
class CreateSeasonUseCaseTest {

    private val seasonRepository = mockk<SeasonRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager)
    private val useCase = CreateSeasonUseCase(seasonRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"

    private fun user(id: String) = User(
        id = id,
        email = "$id@test.com",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        isPlatformAdmin = false,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun startDateAfterEndDateIsRejected() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        val request = CreateSeasonRequest(
            clubId = clubIdA,
            type = SeasonType.EUROPEAN_YEAR,
            startDate = LocalDate(2027, 6, 30), // deliberately after endDate below
            endDate = LocalDate(2026, 7, 1)
        )

        assertFailsWith<ValidationException> {
            useCase(coordinator.id, request)
        }
        Unit
    }

    @Test
    fun equalStartAndEndDatesAreAccepted() = runBlocking {
        // Not explicitly asked for either way -- only "start after end" is a bug -- so a same-day
        // season is left valid rather than inventing an extra, unrequested rule.
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        val sameDay = LocalDate(2026, 7, 1)
        coEvery { seasonRepository.findByDetails(clubIdA, SeasonType.EUROPEAN_YEAR, sameDay, sameDay) } returns null
        coEvery { seasonRepository.createSeason(any()) } answers { firstArg() }
        val request = CreateSeasonRequest(
            clubId = clubIdA,
            type = SeasonType.EUROPEAN_YEAR,
            startDate = sameDay,
            endDate = sameDay
        )

        useCase(coordinator.id, request)
        Unit
    }

    @Test
    fun validDatesForACoordinatorAreCreated() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        val start = LocalDate(2026, 7, 1)
        val end = LocalDate(2027, 6, 30)
        coEvery { seasonRepository.findByDetails(clubIdA, SeasonType.EUROPEAN_YEAR, start, end) } returns null
        coEvery { seasonRepository.createSeason(any()) } answers { firstArg() }
        val request = CreateSeasonRequest(clubId = clubIdA, type = SeasonType.EUROPEAN_YEAR, startDate = start, endDate = end)

        val season = useCase(coordinator.id, request)

        assertEquals(start, season.startDate)
        assertEquals(end, season.endDate)
        assertEquals("26/27", season.name)
    }

    @Test
    fun plainMemberCannotCreateASeason() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        val request = CreateSeasonRequest(
            clubId = clubIdA,
            type = SeasonType.EUROPEAN_YEAR,
            startDate = LocalDate(2026, 7, 1),
            endDate = LocalDate(2027, 6, 30)
        )

        assertFailsWith<ForbiddenException> {
            useCase(member.id, request)
        }
        Unit
    }

    @Test
    fun duplicateSeasonIsRejectedAsAConflict() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        val start = LocalDate(2026, 7, 1)
        val end = LocalDate(2027, 6, 30)
        val existing = Season(
            id = "existing-season",
            clubId = clubIdA,
            name = "26/27",
            type = SeasonType.EUROPEAN_YEAR,
            startDate = start,
            endDate = end,
            active = true
        )
        coEvery { seasonRepository.findByDetails(clubIdA, SeasonType.EUROPEAN_YEAR, start, end) } returns existing
        val request = CreateSeasonRequest(clubId = clubIdA, type = SeasonType.EUROPEAN_YEAR, startDate = start, endDate = end)

        assertFailsWith<ConflictException> {
            useCase(coordinator.id, request)
        }
        Unit
    }
}
