package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.SessionContentCounts
import com.ricardofrango.squadify.domain.repository.StoredTrainingSession
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.CreateTrainingSessionsRequest
import com.ricardofrango.squadify.model.Holiday
import com.ricardofrango.squadify.model.SaveTrainingSessionRequest
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSlot
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// Training sessions (design 39c–f): bulk creation with holidays and replacements, one session, who can.
class TrainingSessionUseCasesTest {
    /** Sessions in memory; [content] fakes exercises and ratings per session. */
    private class FakeSessions : TrainingSessionRepository {
        val sessions = mutableMapOf<String, StoredTrainingSession>()
        val content = mutableMapOf<String, SessionContentCounts>()
        override suspend fun getByAgeGroup(ageGroupId: String) = sessions.values.filter { it.session.ageGroupId == ageGroupId }.sortedBy { it.session.date }
        override suspend fun getById(id: String) = sessions[id]
        override suspend fun insertAll(sessions: List<TrainingSession>) { sessions.forEach { this.sessions[it.id] = StoredTrainingSession(it, null) } }
        override suspend fun update(session: TrainingSession): Boolean { sessions[session.id] = StoredTrainingSession(session, null); return true }
        override suspend fun setCancelled(id: String, at: Instant?, by: String?): Boolean {
            val s = sessions[id] ?: return false
            sessions[id] = StoredTrainingSession(s.session.copy(cancelledAt = at), by); return true
        }
        override suspend fun deleteAll(ids: List<String>) { ids.forEach { sessions.remove(it) } }
        override suspend fun contentCounts(ids: List<String>) = ids.associateWith { content[it] ?: SessionContentCounts(0, 0) }
    }

    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    private val check = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository)
    private val repo = FakeSessions()
    private val create = CreateTrainingSessionsUseCase(ageGroupRepository, seasonRepository, clubRepository, repo, check, transactionManager)
    private val save = SaveTrainingSessionUseCase(ageGroupRepository, seasonRepository, repo, check, transactionManager)
    private val change = ChangeTrainingSessionUseCase(ageGroupRepository, repo, check, transactionManager)
    private val planRepository = mockk<com.ricardofrango.squadify.domain.repository.TrainingPlanRepository>()
    private val exerciseRepository = mockk<com.ricardofrango.squadify.domain.repository.TrainingExerciseRepository>(relaxed = true)
    private val details = GetTrainingSessionUseCase(repo, ageGroupRepository, clubRepository, seasonRepository, playerRepository, planRepository, exerciseRepository, userRepository, check, transactionManager)
    private val exercisesUseCase = TrainingExercisesUseCase(ageGroupRepository, repo, exerciseRepository, check, transactionManager)
    private val get = GetTrainingSessionsUseCase(ageGroupRepository, seasonRepository, clubRepository, repo, userRepository, check, transactionManager)

    private val clubId = "club-1"
    private val ag = AgeGroup("ag-1", clubId, "Sub-15", "s-1", 2012, 2012)
    private val season = Season("s-1", clubId, "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), true)
    private val tue = TrainingSlot(listOf(2), LocalTime(18, 0), LocalTime(19, 0))
    private val fri = TrainingSlot(listOf(5), LocalTime(18, 30), LocalTime(20, 0))
    // Design 39d: Tue + Fri from 6 Oct 2026 to 8 Jan 2027.
    private val design = CreateTrainingSessionsRequest(listOf(tue, fri), LocalDate(2026, 10, 6), LocalDate(2027, 1, 8), location = " Campo do Ribamar ")

    init {
        coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag.id) } returns ag
        coEvery { seasonRepository.getSeasonById(season.id) } returns season
        coEvery { clubRepository.getClubById(clubId) } returns Club(clubId, "CD Ribamar", stadiumLocation = "Campo do Ribamar", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)
    }

    private fun user(clubRole: ClubRole?, teamRole: TeamRole? = null, linkedPlayer: Boolean = false): String {
        val u = User("u-$clubRole-$teamRole-$linkedPlayer", "u@t.com", firstName = "Ricardo", lastName = "Frango", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(u.id) } returns u
        coEvery { userRepository.getUserRoleInClub(u.id, clubId) } returns clubRole
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(u.id, ag.id) } returns teamRole
        return u.id
    }

    private fun existing(id: String, date: LocalDate, start: LocalTime, exercises: Int = 0, ratings: Int = 0) {
        repo.sessions[id] = StoredTrainingSession(TrainingSession(id, ag.id, date, start, start.let { LocalTime(it.hour + 1, it.minute) }), null)
        repo.content[id] = SessionContentCounts(exercises, ratings)
    }

    @Test
    fun theDesignsPeriodMakesTwentyFourSessionsAndSkipsFourHolidays() = runBlocking<Unit> {
        val preview = create.preview(user(ClubRole.COORDINATOR), ag.id, design)
        assertEquals(24, preview.toCreate.size)
        assertEquals(4, preview.skippedHolidays)
        assertEquals(
            setOf(Holiday.RESTORATION_OF_INDEPENDENCE, Holiday.IMMACULATE_CONCEPTION, Holiday.CHRISTMAS, Holiday.NEW_YEAR),
            preview.items.mapNotNull { it.skippedHoliday }.toSet()
        )
        // Previewing creates nothing.
        assertTrue(repo.sessions.isEmpty())
    }

    @Test
    fun withHolidaysIncludedNothingIsSkipped() = runBlocking<Unit> {
        val preview = create.preview(user(ClubRole.COORDINATOR), ag.id, design.copy(includeHolidays = true))
        assertEquals(28, preview.toCreate.size)
        assertEquals(0, preview.skippedHolidays)
    }

    @Test
    fun aSessionAtTheSameDateAndTimeIsReplacedAndContentNeedsConfirming() = runBlocking<Unit> {
        val coordinator = user(ClubRole.COORDINATOR)
        existing("old-1", LocalDate(2026, 10, 9), LocalTime(18, 30), exercises = 4, ratings = 18)
        existing("old-2", LocalDate(2026, 10, 16), LocalTime(18, 30))
        // Same day, different time: not replaced.
        existing("other", LocalDate(2026, 10, 13), LocalTime(10, 0))

        val preview = create.preview(coordinator, ag.id, design)
        assertEquals(2, preview.replacing)
        assertEquals(1, preview.replacingWithContent)
        assertEquals(18, preview.toCreate.first { it.replaces?.sessionId == "old-1" }.replaces!!.ratingCount)

        assertFailsWith<ConflictException> { create.create(coordinator, ag.id, design) }
        assertEquals(3, repo.sessions.size)

        assertEquals(24, create.create(coordinator, ag.id, design.copy(confirmReplacingContent = true)))
        assertFalse("old-1" in repo.sessions)
        assertTrue("other" in repo.sessions)
        assertEquals(25, repo.sessions.size)
        assertEquals("Campo do Ribamar", repo.sessions.values.first { it.session.id != "other" }.session.location)
    }

    @Test
    fun thePeriodMustFallInsideTheSeasonAndSlotsMustMakeSense() = runBlocking<Unit> {
        val coordinator = user(ClubRole.COORDINATOR)
        assertFailsWith<ValidationException> { create.preview(coordinator, ag.id, design.copy(endDate = LocalDate(2027, 7, 31))) }
        assertFailsWith<ValidationException> { create.preview(coordinator, ag.id, design.copy(slots = emptyList())) }
        assertFailsWith<ValidationException> { create.preview(coordinator, ag.id, design.copy(slots = listOf(tue.copy(weekdays = emptyList())))) }
        assertFailsWith<ValidationException> { create.preview(coordinator, ag.id, design.copy(slots = listOf(tue.copy(endTime = LocalTime(17, 0))))) }
    }

    @Test
    fun staffCannotCreateButCanSeeAndRate() = runBlocking<Unit> {
        val staff = user(null, TeamRole.STAFF)
        assertFailsWith<ForbiddenException> { create.preview(staff, ag.id, design) }
        val sessions = get(staff, ag.id)
        assertFalse(sessions.canEdit)
        assertTrue(sessions.canRate)
        assertEquals("Campo do Ribamar", sessions.stadiumLocation)
    }

    @Test
    fun aCoachCanManageTrainingByDefault() = runBlocking<Unit> {
        val sessions = get(user(null, TeamRole.COACH), ag.id)
        assertTrue(sessions.canEdit)
    }

    @Test
    fun aParentOfAPlayerInThePoolSeesSessionsButCannotEditOrRate() = runBlocking<Unit> {
        val parent = user(null)
        val player = com.ricardofrango.squadify.model.Player("p1", clubId, "Tomás", "B", LocalDate(2012, 1, 1), active = true)
        coEvery { playerRepository.getPlayersByUser(parent) } returns listOf(player)
        coEvery { playerRepository.getPlayersByAgeGroup(ag.id) } returns listOf(player)
        val sessions = get(parent, ag.id)
        assertFalse(sessions.canRate)
        assertFalse(sessions.canEdit)
    }

    @Test
    fun oneSessionMustEndAfterItStartsAndReplacesAClash() = runBlocking<Unit> {
        val coordinator = user(ClubRole.COORDINATOR)
        val request = SaveTrainingSessionRequest(LocalDate(2026, 10, 17), LocalTime(10, 0), LocalTime(11, 30), notes = " Extra session ")
        assertFailsWith<ValidationException> { save.add(coordinator, ag.id, request.copy(endTime = LocalTime(9, 0))) }
        existing("clash", LocalDate(2026, 10, 17), LocalTime(10, 0))
        val added = save.add(coordinator, ag.id, request)
        assertEquals("Extra session", added.notes)
        assertFalse("clash" in repo.sessions)
        assertEquals(1, repo.sessions.size)
    }

    @Test
    fun cancellingKeepsTheSessionAndRecordsWhoRestoringClearsIt() = runBlocking<Unit> {
        val coordinator = user(ClubRole.COORDINATOR)
        existing("s1", LocalDate(2026, 10, 22), LocalTime(18, 30), exercises = 3)
        change.cancel(coordinator, "s1")
        assertNotNull(repo.sessions["s1"]?.session?.cancelledAt)
        assertEquals("Ricardo Frango", get(coordinator, ag.id).sessions.single().cancelledByName)
        change.restore(coordinator, "s1")
        assertEquals(null, repo.sessions["s1"]?.session?.cancelledAt)
        change.delete(coordinator, "s1")
        assertTrue(repo.sessions.isEmpty())
    }

    @Test
    fun staffCannotCancel() = runBlocking<Unit> {
        existing("s1", LocalDate(2026, 10, 22), LocalTime(18, 30))
        assertFailsWith<ForbiddenException> { change.cancel(user(null, TeamRole.STAFF), "s1") }
    }

    @Test
    fun theDetailsCarryTheWeekAndMonthPlansForStaffOnly() = runBlocking<Unit> {
        existing("s1", LocalDate(2026, 10, 15), LocalTime(18, 30))
        coEvery { planRepository.getByAgeGroup(ag.id) } returns listOf(
            com.ricardofrango.squadify.model.TrainingPlan("w", ag.id, com.ricardofrango.squadify.model.TrainingPlanLevel.WEEK, LocalDate(2026, 10, 12), focus = "Playing out"),
            com.ricardofrango.squadify.model.TrainingPlan("m", ag.id, com.ricardofrango.squadify.model.TrainingPlanLevel.MONTH, LocalDate(2026, 10, 1), focus = "Building from the back")
        )
        val player = com.ricardofrango.squadify.model.Player("p1", clubId, "Tomás", "B", LocalDate(2012, 1, 1), active = true)
        coEvery { playerRepository.getPlayersByAgeGroup(ag.id) } returns listOf(player)

        val staffView = details(user(null, TeamRole.STAFF), "s1")
        assertEquals("Playing out", staffView.weekPlan?.focus)
        assertEquals("Building from the back", staffView.monthPlan?.focus)
        assertEquals(1, staffView.poolSize)

        val parent = user(null)
        coEvery { playerRepository.getPlayersByUser(parent) } returns listOf(player)
        val parentView = details(parent, "s1")
        assertEquals(null, parentView.weekPlan)
        assertFalse(parentView.canRate)
    }

    private fun library(vararg ids: String) {
        coEvery { exerciseRepository.getByAgeGroup(ag.id) } returns ids.mapIndexed { i, id ->
            com.ricardofrango.squadify.model.TrainingExercise(id, ag.id, "Exercise $id", usedCount = i)
        }
    }

    @Test
    fun theLibraryIsMostUsedFirstAndForStaffOnly() = runBlocking<Unit> {
        library("a", "b", "c")
        assertEquals(listOf("c", "b", "a"), exercisesUseCase.library(user(null, TeamRole.STAFF), ag.id).map { it.id })
        assertFailsWith<ForbiddenException> { exercisesUseCase.library(user(ClubRole.MEMBER), ag.id) }
    }

    @Test
    fun anExerciseNeedsANameAndAPositiveDuration() = runBlocking<Unit> {
        val coordinator = user(ClubRole.COORDINATOR)
        val request = com.ricardofrango.squadify.model.SaveExerciseRequest(name = "  ")
        assertFailsWith<ValidationException> { exercisesUseCase.create(coordinator, ag.id, request) }
        assertFailsWith<ValidationException> { exercisesUseCase.create(coordinator, ag.id, request.copy(name = "Rondo 4v1", durationMinutes = 0)) }
        coEvery { exerciseRepository.create(any()) } answers { firstArg() }
        val created = exercisesUseCase.create(coordinator, ag.id, request.copy(name = " Rondo 4v1 ", durationMinutes = 15, description = " "))
        assertEquals("Rondo 4v1", created.name)
        assertEquals(null, created.description)
        assertEquals(ag.id, created.ageGroupId)
    }

    @Test
    fun aSessionTakesItsOwnLibrarysExercisesOnceAndNotWhileCancelled() = runBlocking<Unit> {
        val coordinator = user(ClubRole.COORDINATOR)
        existing("s1", LocalDate(2026, 10, 15), LocalTime(18, 30))
        library("a", "b")
        assertFailsWith<ValidationException> { exercisesUseCase.setForSession(coordinator, "s1", listOf("a", "a")) }
        assertFailsWith<ValidationException> { exercisesUseCase.setForSession(coordinator, "s1", listOf("a", "elsewhere")) }
        exercisesUseCase.setForSession(coordinator, "s1", listOf("b", "a"))
        io.mockk.coVerify { exerciseRepository.setForSession("s1", listOf("b", "a")) }
        change.cancel(coordinator, "s1")
        assertFailsWith<ValidationException> { exercisesUseCase.setForSession(coordinator, "s1", listOf("a")) }
    }

    @Test
    fun staffCannotChangeASessionsExercises() = runBlocking<Unit> {
        existing("s1", LocalDate(2026, 10, 15), LocalTime(18, 30))
        library("a")
        assertFailsWith<ForbiddenException> { exercisesUseCase.setForSession(user(null, TeamRole.STAFF), "s1", listOf("a")) }
    }
}
