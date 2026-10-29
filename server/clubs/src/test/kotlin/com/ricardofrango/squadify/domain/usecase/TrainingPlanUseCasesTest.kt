package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.TrainingPlanRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.SaveTrainingPlanRequest
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.TrainingIntensity
import com.ricardofrango.squadify.model.TrainingPhase
import com.ricardofrango.squadify.model.TrainingPlan
import com.ricardofrango.squadify.model.TrainingPlanLevel
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
    private val getPlans = GetTrainingPlansUseCase(ageGroupRepository, seasonRepository, clubRepository, trainingPlanRepository, checkUserPermissionUseCase, transactionManager)
    private val savePlan = SaveTrainingPlanUseCase(ageGroupRepository, seasonRepository, trainingPlanRepository, checkUserPermissionUseCase, transactionManager)

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
