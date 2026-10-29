package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.model.MatchRecordEvent
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SaveMatchRecordRequest
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class SaveMatchRecordUseCaseTest {

    private val matchRepository = mockk<MatchRepository>(relaxed = true)
    private val eventRepository = mockk<MatchEventRepository>(relaxed = true)
    private val statsRepository = mockk<MatchPlayerStatsRepository>(relaxed = true)
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val notify = mockk<NotifyMatchEventUseCase>(relaxed = true)
    private val clubMatchesBus = mockk<ClubMatchesBus>(relaxed = true)
    private val now = Instant.fromEpochMilliseconds(10_000_000)
    private val clubRepository = mockk<ClubRepository>().also {
        coEvery { it.getClubById(any()) } returns Club("club-1", "FC", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)
    }
    private val useCase = SaveMatchRecordUseCase(
        matchRepository, eventRepository, statsRepository, mockk<LiveMatchBus>(relaxed = true),
        ClubAccessPolicy(checkUserPermissionUseCase), notify, clubMatchesBus, transactionManager, clubRepository, now = { now }
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ageGroupId = "ag-1"
    private val staff = "staff"

    // Played live: Ana and Bia started, Caro came on; Duda stayed on the bench.
    private val finished = Match(
        matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.FINISHED,
        halfLengthMinutes = 25, firstHalfEndMinute = 26, fullTimeMinute = 52, kickoffAt = Instant.fromEpochMilliseconds(1)
    )
    private val notPlayed = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(5_000_000))

    init {
        coEvery { userRepository.findById(staff) } returns
            User(staff, "s@t.com", firstName = "S", lastName = "T", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.getUserRoleInClub(staff, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff, ageGroupId) } returns AgeGroupRole.STAFF
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ageGroupId, "s-1", "Sub-11 A", "A", active = true)
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns listOf("ana", "bia", "caro", "duda").map { callUp(it, starting = it == "ana" || it == "bia") }
        coEvery { eventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 1, 0)
        coEvery { eventRepository.addEvent(any()) } answers { firstArg() }
    }

    private fun callUp(playerId: String, starting: Boolean) = MatchCallUpWithPlayer(
        MatchCallUp("cu-$playerId", matchId, playerId, CallUpStatus.CALLED_UP, isStarting = starting),
        Player(playerId, clubId, playerId, "X", LocalDate(2015, 1, 1), active = true)
    )

    private fun event(id: String, type: EventType, playerId: String?, minute: Int = 10, related: String? = null, opponent: Boolean = false) =
        MatchEvent(id, matchId, type, minute, playerId = playerId, isOpponent = opponent, createdAt = Instant.fromEpochMilliseconds(2), period = 1, relatedEventId = related)

    private fun stats(playerId: String, started: Boolean, minutes: Int, rating: Int? = null) =
        MatchPlayerStats(matchId, playerId, started, minutes, 0, 0, 0, 0, 0, rating)

    private fun stubFinished(events: List<MatchEvent>) {
        coEvery { matchRepository.getMatchById(matchId) } returns finished
        coEvery { eventRepository.getEventsByMatch(matchId) } returns events
        coEvery { statsRepository.getByMatch(matchId) } returns
            listOf(stats("ana", true, 52, rating = 8), stats("bia", true, 30), stats("caro", false, 22), stats("duda", false, 0))
    }

    @Test
    fun aGoalLoggedWithoutItsScorerGetsOneAndTheMinutesStay() = runBlocking {
        val subIn = event("sub-in", EventType.SUBSTITUTION_IN, "caro", minute = 30)
        val subOut = event("sub-out", EventType.SUBSTITUTION_OUT, "bia", minute = 30)
        stubFinished(listOf(event("g1", EventType.GOAL, "ana"), subIn, subOut))
        coEvery { eventRepository.getEventsByMatch(matchId) } returnsMany listOf(
            listOf(event("g1", EventType.GOAL, "ana"), subIn, subOut),
            listOf(event("g1", EventType.GOAL, "caro", minute = 40), event("a1", EventType.ASSIST, "ana", minute = 40, related = "g1"), subIn, subOut)
        )
        val lines = slot<List<MatchPlayerStats>>()
        coEvery { statsRepository.replaceForMatch(matchId, capture(lines)) } returns Unit

        useCase(staff, matchId, SaveMatchRecordRequest(listOf(MatchRecordEvent("g1", EventType.GOAL, 40, playerId = "caro", assistPlayerId = "ana"))))

        coVerify { eventRepository.updateEvent(match { it.id == "g1" && it.playerId == "caro" && it.minute == 40 }) }
        coVerify { eventRepository.addEvent(match { it.type == EventType.ASSIST && it.playerId == "ana" && it.relatedEventId == "g1" }) }
        // Substitutions are never touched.
        coVerify(exactly = 0) { eventRepository.deleteEvent("sub-in") }
        coVerify(exactly = 0) { eventRepository.deleteEvent("sub-out") }
        val byPlayer = lines.captured.associateBy { it.playerId }
        assertEquals(1, byPlayer.getValue("caro").goals)
        assertEquals(1, byPlayer.getValue("ana").assists)
        assertEquals(listOf(52, 30, 22, 0), listOf("ana", "bia", "caro", "duda").map { byPlayer.getValue(it).minutesPlayed })
        assertEquals(8, byPlayer.getValue("ana").rating)
        coVerify(exactly = 0) { notify(any(), any(), any()) }
    }

    @Test
    fun aForgottenCardIsAddedAndARemovedGoalTakesItsAssist() = runBlocking {
        stubFinished(listOf(event("g1", EventType.GOAL, "ana"), event("a1", EventType.ASSIST, "bia", related = "g1")))

        useCase(staff, matchId, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.YELLOW_CARD, minute = 12, playerId = "duda"))))

        coVerify { eventRepository.deleteEvent("a1") }
        coVerify { eventRepository.deleteEvent("g1") }
        coVerify { eventRepository.addEvent(match { it.type == EventType.YELLOW_CARD && it.playerId == "duda" && it.createdAt == now }) }
    }

    @Test
    fun aMatchFollowedLiveKeepsItsMinutes() = runBlocking {
        stubFinished(emptyList())
        assertFailsWith<ValidationException> {
            useCase(staff, matchId, SaveMatchRecordRequest(emptyList(), minutesPlayed = mapOf("ana" to 10)))
        }
        Unit
    }

    @Test
    fun substitutionsCantBeEntered() = runBlocking {
        stubFinished(emptyList())
        assertFailsWith<ValidationException> {
            useCase(staff, matchId, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.SUBSTITUTION_IN, minute = 5, playerId = "duda"))))
        }
        Unit
    }

    @Test
    fun aPlayerWhoNeverCameOnCantScore() = runBlocking {
        stubFinished(emptyList())
        assertFailsWith<ValidationException> {
            useCase(staff, matchId, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.GOAL, minute = 5, playerId = "duda"))))
        }
        Unit
    }

    @Test
    fun ourGoalCanHaveNoScorerButNotAnAssistWithout() = runBlocking {
        stubFinished(emptyList())
        useCase(staff, matchId, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.GOAL, minute = 5))))
        coVerify { eventRepository.addEvent(match { it.type == EventType.GOAL && it.playerId == null && !it.isOpponent }) }
        assertFailsWith<ValidationException> {
            useCase(staff, matchId, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.GOAL, minute = 5, assistPlayerId = "ana"))))
        }
        assertFailsWith<ValidationException> {
            useCase(staff, matchId, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.YELLOW_CARD, minute = 5))))
        }
        Unit
    }

    @Test
    fun theOpponentsGoalsHaveNoPlayer() = runBlocking {
        stubFinished(emptyList())
        assertFailsWith<ValidationException> {
            useCase(staff, matchId, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.GOAL, minute = 5, playerId = "ana", isOpponent = true))))
        }
        assertFailsWith<ValidationException> {
            useCase(staff, matchId, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.YELLOW_CARD, minute = 5, isOpponent = true))))
        }
        Unit
    }

    @Test
    fun aGameNotFollowedLiveIsEnteredWithItsMinutesAndOnePush() = runBlocking {
        coEvery { matchRepository.getMatchById(matchId) } returns notPlayed
        coEvery { eventRepository.getEventsByMatch(matchId) } returnsMany listOf(
            emptyList(),
            listOf(event("new", EventType.GOAL, "bia", minute = 31), event("red", EventType.RED_CARD, "duda", minute = 40))
        )
        coEvery { statsRepository.getByMatch(matchId) } returns emptyList()
        val lines = slot<List<MatchPlayerStats>>()
        coEvery { statsRepository.replaceForMatch(matchId, capture(lines)) } returns Unit

        val saved = useCase(
            staff, matchId,
            SaveMatchRecordRequest(
                events = listOf(
                    MatchRecordEvent(type = EventType.GOAL, minute = 31, period = 2, playerId = "bia"),
                    MatchRecordEvent(type = EventType.GOAL, minute = 44, period = 2, isOpponent = true),
                    MatchRecordEvent(type = EventType.RED_CARD, minute = 40, period = 2, playerId = "duda")
                ),
                halfLengthMinutes = 25,
                minutesPlayed = mapOf("ana" to 50, "bia" to 25, "caro" to 25)
            )
        )

        assertEquals(MatchStatus.FINISHED, saved.status)
        assertTrue(saved.enteredAfterGame)
        assertEquals(50, saved.fullTimeMinute)
        coVerify { matchRepository.markEnteredAfterGame(matchId, 25, 2) }
        val byPlayer = lines.captured.associateBy { it.playerId }
        // Typed in, not worked out from substitutions; a player left out played 0.
        assertEquals(listOf(50, 25, 25, 0), listOf("ana", "bia", "caro", "duda").map { byPlayer.getValue(it).minutesPlayed })
        assertEquals(1, byPlayer.getValue("bia").goals)
        assertEquals(1, byPlayer.getValue("duda").redCards)
        coVerify(exactly = 1) { notify(matchId, MatchNotificationEvent.FULL_TIME, null) }
        coVerify { clubMatchesBus.publish(clubId, ClubMatchesUpdate(match = saved)) }
    }

    @Test
    fun aGameNotFollowedLiveSaysHowManyASideItWasPlayed() = runBlocking {
        coEvery { matchRepository.getMatchById(matchId) } returns notPlayed
        coEvery { eventRepository.getEventsByMatch(matchId) } returns emptyList()
        coEvery { statsRepository.getByMatch(matchId) } returns emptyList()
        coEvery { matchRepository.updateMatch(any()) } returns true

        val saved = useCase(staff, matchId, SaveMatchRecordRequest(emptyList(), halfLengthMinutes = 25, teamSize = 7))

        assertEquals(7, saved.teamSize)
        coVerify { matchRepository.updateMatch(notPlayed.copy(teamSize = 7)) }
        // A size the sport isn't played with is refused.
        assertFailsWith<ValidationException> { useCase(staff, matchId, SaveMatchRecordRequest(emptyList(), halfLengthMinutes = 25, teamSize = 6)) }
        Unit
    }

    @Test
    fun aMatchFollowedLiveKeepsItsTeamSize() = runBlocking {
        stubFinished(emptyList())
        assertFailsWith<ValidationException> { useCase(staff, matchId, SaveMatchRecordRequest(emptyList(), teamSize = 7)) }
        Unit
    }

    @Test
    fun aGameEnteredAfterwardsStaysEditableWithoutAnotherPush() = runBlocking {
        coEvery { matchRepository.getMatchById(matchId) } returns
            notPlayed.copy(status = MatchStatus.FINISHED, enteredAfterGame = true, halfLengthMinutes = 25, firstHalfEndMinute = 25, fullTimeMinute = 50)
        coEvery { eventRepository.getEventsByMatch(matchId) } returns emptyList()
        coEvery { statsRepository.getByMatch(matchId) } returns listOf(stats("ana", false, 50, rating = 7))
        val lines = slot<List<MatchPlayerStats>>()
        coEvery { statsRepository.replaceForMatch(matchId, capture(lines)) } returns Unit

        useCase(staff, matchId, SaveMatchRecordRequest(emptyList(), minutesPlayed = mapOf("ana" to 40, "bia" to 10)))

        val ana = lines.captured.single { it.playerId == "ana" }
        assertEquals(40, ana.minutesPlayed)
        assertEquals(7, ana.rating)
        coVerify(exactly = 0) { notify(any(), any(), any()) }
    }

    @Test
    fun aGameNotFollowedLiveNeedsItsKickoffToHavePassedAndACallUp() = runBlocking {
        coEvery { matchRepository.getMatchById(matchId) } returns notPlayed.copy(dateTime = Instant.fromEpochMilliseconds(20_000_000))
        coEvery { eventRepository.getEventsByMatch(matchId) } returns emptyList()
        assertFailsWith<ValidationException> { useCase(staff, matchId, SaveMatchRecordRequest(emptyList(), halfLengthMinutes = 25)) }

        coEvery { matchRepository.getMatchById(matchId) } returns notPlayed
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns emptyList()
        assertFailsWith<ValidationException> { useCase(staff, matchId, SaveMatchRecordRequest(emptyList(), halfLengthMinutes = 25)) }
        Unit
    }

    @Test
    fun minutesCantBeMoreThanTheMatch() = runBlocking {
        coEvery { matchRepository.getMatchById(matchId) } returns notPlayed
        coEvery { eventRepository.getEventsByMatch(matchId) } returns emptyList()
        coEvery { statsRepository.getByMatch(matchId) } returns emptyList()
        assertFailsWith<ValidationException> {
            useCase(staff, matchId, SaveMatchRecordRequest(emptyList(), halfLengthMinutes = 25, minutesPlayed = mapOf("ana" to 51)))
        }
        Unit
    }

    @Test
    fun aLiveMatchHasNoRecordToEditYet() = runBlocking {
        coEvery { matchRepository.getMatchById(matchId) } returns finished.copy(status = MatchStatus.LIVE)
        assertFailsWith<ValidationException> { useCase(staff, matchId, SaveMatchRecordRequest(emptyList())) }
        Unit
    }
}
