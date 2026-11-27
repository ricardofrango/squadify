package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.ClubAccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.TrainingPlanRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.model.Club
import com.sportssoftw.squadcall.model.ClubPermissionSetting
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.ClubStatus
import com.sportssoftw.squadcall.model.ConfigurablePermission
import com.sportssoftw.squadcall.model.SaveTrainingPlanRequest
import com.sportssoftw.squadcall.model.Season
import com.sportssoftw.squadcall.model.SeasonType
import com.sportssoftw.squadcall.model.SportType
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.TrainingIntensity
import com.sportssoftw.squadcall.model.TrainingPhase
import com.sportssoftw.squadcall.model.TrainingPlan
import com.sportssoftw.squadcall.model.TrainingPlanLevel
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

// Training plans (design 40): who reads and edits them, and what a valid plan is.
class TrainingPlanUseCasesTest {
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val trainingPlanRepository = mockk<TrainingPlanRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val getPlans = GetTrainingPlansUseCase(ageGroupRepository, seasonRepository, clubRepository, trainingPlanRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager)
    private val savePlan = SaveTrainingPlanUseCase(ageGroupRepository, seasonRepository, trainingPlanRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager)

    private val clubId = "club-1"
    private val ag = AgeGroup("ag-1", clubId, "Sub-15", "s-1", 2012, 2012)
    private val season = Season("s-1", clubId, "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), true)
    private val saved = slot<TrainingPlan>()

    init {
        coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag.id) } returns ag
        coEvery { seasonRepository.getSeasonById(season.id) } returns season
        coEvery { clubRepository.getClubById(clubId) } returns Club(clubId, "CD Ribamar", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)
        coEvery { trainingPlanRepository.getByAgeGroup(ag.id) } returns emptyList()
        coEvery { trainingPlanRepository.save(capture(saved), any()) } answers { firstArg() }
    }

    private fun user(clubRole: ClubRole?, teamRole: AgeGroupRole? = null): String {
        val u = User("u-$clubRole-$teamRole", "u@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(u.id) } returns u
        coEvery { userRepository.getUserRoleInClub(u.id, clubId) } returns clubRole
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(u.id, ag.id) } returns teamRole
        return u.id
    }

    private fun month(focus: String? = "Building from the back") =
        SaveTrainingPlanRequest(TrainingPlanLevel.MONTH, LocalDate(2026, 10, 1), focus = focus, objectives = listOf(" Third-man runs ", " "))

    @Test
    fun aCoachCanEditByDefaultAndStaffCanOnlyRead() = runBlocking<Unit> {
        assertTrue(getPlans(user(null, AgeGroupRole.COACH), ag.id).canEdit)
        assertFalse(getPlans(user(null, AgeGroupRole.STAFF), ag.id).canEdit)
        assertFailsWith<ForbiddenException> { savePlan(user(null, AgeGroupRole.STAFF), ag.id, month()) }
    }

    @Test
    fun aClubSetToCoordinatorsOnlyKeepsCoachesOut() = runBlocking<Unit> {
        coEvery { clubPermissionRepository.getOverride(clubId, ConfigurablePermission.MANAGE_TRAINING) } returns
            ClubPermissionSetting(ConfigurablePermission.MANAGE_TRAINING, minAgeGroupRole = null)
        assertFailsWith<ForbiddenException> { savePlan(user(null, AgeGroupRole.COACH), ag.id, month()) }
    }

    @Test
    fun someoneOutsideTheAgeGroupAndClubCannotRead() = runBlocking<Unit> {
        assertFailsWith<ForbiddenException> { getPlans(user(null), ag.id) }
    }

    @Test
    fun aMonthPlanIsTrimmedAndBlankObjectivesDropped() = runBlocking<Unit> {
        savePlan(user(ClubRole.COORDINATOR), ag.id, month())
        assertEquals(listOf("Third-man runs"), saved.captured.objectives)
        assertEquals("Building from the back", saved.captured.focus)
    }

    @Test
    fun monthsAndWeeksNeedAFocus() = runBlocking<Unit> {
        assertFailsWith<ValidationException> { savePlan(user(ClubRole.COORDINATOR), ag.id, month(focus = "  ")) }
    }

    @Test
    fun thePeriodMustBeOneOfTheSeasons() = runBlocking<Unit> {
        val id = user(ClubRole.COORDINATOR)
        assertFailsWith<ValidationException> { savePlan(id, ag.id, month().copy(periodStart = LocalDate(2026, 10, 2))) }
        assertFailsWith<ValidationException> {
            savePlan(id, ag.id, SaveTrainingPlanRequest(TrainingPlanLevel.WEEK, LocalDate(2026, 10, 13), focus = "Pressing"))
        }
    }

    @Test
    fun onlyAWeekHasAnIntensityAndOnlyTheSeasonHasPhases() = runBlocking<Unit> {
        val id = user(ClubRole.COORDINATOR)
        assertFailsWith<ValidationException> { savePlan(id, ag.id, month().copy(intensity = TrainingIntensity.HIGH)) }
        val phase = TrainingPhase("1st half", LocalDate(2026, 9, 28), LocalDate(2026, 12, 20))
        assertFailsWith<ValidationException> { savePlan(id, ag.id, month().copy(phases = listOf(phase))) }
        savePlan(id, ag.id, SaveTrainingPlanRequest(TrainingPlanLevel.WEEK, LocalDate(2026, 10, 12), focus = "Pressing", intensity = TrainingIntensity.MEDIUM))
        assertEquals(TrainingIntensity.MEDIUM, saved.captured.intensity)
    }

    @Test
    fun seasonDatesMustFallInsideTheSeason() = runBlocking<Unit> {
        val id = user(ClubRole.COORDINATOR)
        val season = SaveTrainingPlanRequest(TrainingPlanLevel.SEASON, LocalDate(2026, 9, 1))
        assertFailsWith<ValidationException> {
            savePlan(id, ag.id, season.copy(phases = listOf(TrainingPhase("2nd half", LocalDate(2027, 1, 5), LocalDate(2027, 7, 15)))))
        }
        assertFailsWith<ValidationException> {
            savePlan(id, ag.id, season.copy(phases = listOf(TrainingPhase("Backwards", LocalDate(2026, 12, 1), LocalDate(2026, 11, 1)))))
        }
    }

    @Test
    fun savingAgainReplacesThePlanOfThatPeriod() = runBlocking<Unit> {
        val existing = TrainingPlan("plan-1", ag.id, TrainingPlanLevel.MONTH, LocalDate(2026, 10, 1), focus = "Old")
        coEvery { trainingPlanRepository.getByAgeGroup(ag.id) } returns listOf(existing)
        savePlan(user(ClubRole.COORDINATOR), ag.id, month())
        assertEquals("plan-1", saved.captured.id)
    }
}
