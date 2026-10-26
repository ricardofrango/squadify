package com.ricardofrango.squadify.presentation.ui.livematch

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.usecase.AddMatchEventUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteMatchEventUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchEventsUseCase
import com.ricardofrango.squadify.domain.usecase.GetMatchScoreUseCase
import com.ricardofrango.squadify.domain.usecase.ObserveLiveMatchUseCase
import com.ricardofrango.squadify.domain.usecase.PauseMatchUseCase
import com.ricardofrango.squadify.domain.usecase.ResumeMatchUseCase
import com.ricardofrango.squadify.domain.usecase.SetMatchLineupUseCase
import com.ricardofrango.squadify.domain.usecase.SetMatchMinuteUseCase
import com.ricardofrango.squadify.domain.usecase.StartMatchUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateMatchStatusUseCase
import com.ricardofrango.squadify.model.ClubMatchesUpdate
import com.ricardofrango.squadify.model.CreateMatchEventRequest
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.LiveMatchUpdate
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchLineup
import com.ricardofrango.squadify.model.MatchReport
import com.ricardofrango.squadify.model.MatchReportDetails
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.SaveMatchReportRequest
import com.ricardofrango.squadify.model.SetMatchLineupRequest
import com.ricardofrango.squadify.model.StartMatchRequest
import com.ricardofrango.squadify.model.UpdateMatchRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The live console's quick actions in a row (the de-sync seen in a real game, 2026-10-24): a swap
 * or substitution made before the previous one's reply came back must not move the wrong players.
 * The fake server holds each reply until the test releases it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveMatchSyncTest {

    private val match = Match(
        id = "m1", clubId = "c1", teamId = "t1", opponentId = "o1", dateTime = Instant.fromEpochMilliseconds(0),
        status = MatchStatus.LIVE, kickoffAt = Instant.fromEpochMilliseconds(0)
    )
    // p1..p7 in slots 0..6, p8 and p9 on the bench.
    private val start = previewLiveDetails(match)
    private val startSlots = start.storedSlots()

    private class FakeServer(var details: MatchDetails) : MatchRepository {
        val socket = MutableSharedFlow<LiveMatchUpdate>()
        val lineupsSent = mutableListOf<Map<Int, String>>()
        val eventsSent = mutableListOf<Pair<EventType, String?>>()
        // A reply is held while its gate is set; the test completes it (or fails it).
        var lineupGate: CompletableDeferred<Unit>? = null
        var eventGate: CompletableDeferred<Unit>? = null
        private var nextEventId = 0

        override suspend fun getMatchDetails(matchId: String) = details
        override suspend fun getMatchEvents(matchId: String) = emptyList<MatchEvent>()
        override suspend fun getMatchScore(matchId: String) = MatchScore(matchId, 0, 0)
        override suspend fun setLineup(matchId: String, request: SetMatchLineupRequest): MatchLineup {
            lineupsSent += request.slots
            lineupGate?.await()
            details = details.withLineup(MatchLineup(request.formation, request.slots))
            return MatchLineup(request.formation, request.slots)
        }
        override suspend fun addMatchEvent(matchId: String, request: CreateMatchEventRequest): MatchEvent {
            eventsSent += request.type to request.playerId
            eventGate?.await()
            return MatchEvent("e${nextEventId++}", matchId, request.type, request.minute, request.playerId, createdAt = Instant.fromEpochMilliseconds(nextEventId.toLong()))
        }
        override fun observeLiveMatch(matchId: String): Flow<LiveMatchUpdate> = socket

        override suspend fun createMatch(request: CreateMatchRequest): Match = error("unused")
        override suspend fun getMatches(clubId: String): List<Match> = error("unused")
        override suspend fun updateMatch(matchId: String, request: UpdateMatchRequest) = error("unused")
        override suspend fun deleteMatch(matchId: String) = error("unused")
        override suspend fun updateMatchStatus(matchId: String, status: MatchStatus) = error("unused")
        override suspend fun startMatch(matchId: String, request: StartMatchRequest) = error("unused")
        override suspend fun pauseMatch(matchId: String) = error("unused")
        override suspend fun resumeMatch(matchId: String) = error("unused")
        override suspend fun setMatchMinute(matchId: String, minute: Int) = error("unused")
        override suspend fun manageCallUps(matchId: String, playerIds: List<String>) = error("unused")
        override suspend fun getMatchReport(matchId: String): MatchReportDetails = error("unused")
        override suspend fun saveMatchReport(matchId: String, request: SaveMatchReportRequest): MatchReport = error("unused")
        override suspend fun deleteMatchEvent(matchId: String, eventId: String) = error("unused")
        override fun observeClubMatches(clubId: String): Flow<ClubMatchesUpdate> = error("unused")
    }

    private val server = FakeServer(start)
    private val viewModel = LiveMatchViewModel(
        GetMatchDetailsUseCase(server), GetMatchEventsUseCase(server), GetMatchScoreUseCase(server), StartMatchUseCase(server),
        UpdateMatchStatusUseCase(server), PauseMatchUseCase(server), ResumeMatchUseCase(server), SetMatchMinuteUseCase(server),
        AddMatchEventUseCase(server), DeleteMatchEventUseCase(server), ObserveLiveMatchUseCase(server), SetMatchLineupUseCase(server)
    )

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val idle get() = viewModel.state.value as LiveMatchUiState.Idle
    private val shown get() = idle.details.storedSlots()
    private fun swap(a: Int, b: Int): Map<Int, String> = shown.swapped(a, b).also { viewModel.onSetLineup("2-3-1", it) }
    private fun echo(slots: Map<Int, String>) = LiveMatchUpdate(score = MatchScore("m1", 0, 0), lineup = MatchLineup("2-3-1", slots))

    @Test
    fun swapsMadeBeforeTheReplyAreSentInOrderTheLatestOnce() = runTest {
        viewModel.load("m1")
        server.lineupGate = CompletableDeferred()
        val first = swap(1, 2)
        swap(3, 4)
        val third = swap(5, 6)
        // Only the first is on its way; the screen shows the latest.
        assertEquals(listOf(first), server.lineupsSent)
        assertEquals(third, shown)

        server.lineupGate!!.complete(Unit)
        // The two made meanwhile go as one, the latest, after the first returns.
        assertEquals(listOf(first, third), server.lineupsSent)
        assertEquals(third, server.details.storedSlots())
    }

    @Test
    fun saveIsOffUntilThePitchDiffersFromHowItOpened() = runTest {
        viewModel.load("m1")
        assertEquals(false, idle.lineupChanged)
        swap(1, 2)
        assertEquals(true, idle.lineupChanged)
        // Swapped back: the same pitch it opened with, nothing to save.
        swap(1, 2)
        assertEquals(false, idle.lineupChanged)
    }

    @Test
    fun saveLeavesOnlyOnceThePendingLineupIsOnTheServer() = runTest {
        viewModel.load("m1")
        server.lineupGate = CompletableDeferred()
        val moved = swap(1, 2)
        var left = false
        viewModel.onSaveLineup { left = true }
        assertEquals(false, left)

        server.lineupGate!!.complete(Unit)
        assertEquals(true, left)
        assertEquals(moved, server.details.storedSlots())
    }

    @Test
    fun aRefusedSaveStaysWithTheError() = runTest {
        viewModel.load("m1")
        server.lineupGate = CompletableDeferred()
        swap(1, 2)
        var left = false
        viewModel.onSaveLineup { left = true }

        server.lineupGate!!.completeExceptionally(IllegalStateException("refused"))
        assertEquals(false, left)
    }

    @Test
    fun anOlderEchoDoesNotPutThePitchBack() = runTest {
        viewModel.load("m1")
        val first = swap(1, 2)
        server.lineupGate = CompletableDeferred()
        val second = swap(3, 4)
        // The first swap's echo arrives after the second swap was made.
        server.socket.emit(echo(first))
        assertEquals(second, shown)

        server.lineupGate!!.complete(Unit)
        server.socket.emit(echo(second))
        assertEquals(second, shown)
        // The next swap is worked out from the right pitch: p4 and p5 (slots 3 and 4 swapped) stay put.
        val third = swap(1, 6)
        assertEquals(listOf("p1", "p7", "p2", "p5", "p4", "p6", "p3"), (0..6).map { third.getValue(it) })

        // Once ours is back, another coach's change is taken in.
        server.socket.emit(echo(third))
        val elsewhere = third.swapped(0, 1)
        server.socket.emit(echo(elsewhere))
        assertEquals(elsewhere, shown)
    }

    @Test
    fun aSubstitutionDoesNotWipeASwapMadeWhileItWasSending() = runTest {
        viewModel.load("m1")
        server.eventGate = CompletableDeferred()
        viewModel.onSubstitute(outPlayerId = "p7", inPlayerId = "p8", minute = 10)
        val swapped = swap(1, 2)

        server.eventGate!!.complete(Unit)
        // Both stand: the swap on the pitch, the substitution in the log.
        assertEquals(swapped, shown)
        assertEquals(listOf(EventType.SUBSTITUTION_OUT, EventType.SUBSTITUTION_IN), idle.events.map { it.type })
        assertEquals(false, idle.isSubmitting)
    }

    @Test
    fun aSwapThenASubstitutionReachTheServerInThatOrder() = runTest {
        viewModel.load("m1")
        server.lineupGate = CompletableDeferred()
        val swapped = swap(1, 2)
        viewModel.onSubstitute(outPlayerId = "p2", inPlayerId = "p8", minute = 10)
        // The substitution waits for the swap to be saved.
        assertEquals(emptyList<Pair<EventType, String?>>(), server.eventsSent)

        server.lineupGate!!.complete(Unit)
        assertEquals(listOf<Pair<EventType, String?>>(EventType.SUBSTITUTION_OUT to "p2", EventType.SUBSTITUTION_IN to "p8"), server.eventsSent)
        assertEquals(swapped, shown)
        // p8 comes on where p2 stood after the swap (slot 2).
        assertEquals("p8", idle.details.currentSlots(idle.events, (startSlots.values - "p2" + "p8").toSet())[2])
    }

    @Test
    fun twoSubstitutionsInARowAreNotInterleaved() = runTest {
        viewModel.load("m1")
        server.eventGate = CompletableDeferred()
        viewModel.onSubstitute(outPlayerId = "p6", inPlayerId = "p8", minute = 10)
        viewModel.onSubstitute(outPlayerId = "p7", inPlayerId = "p9", minute = 10)
        server.eventGate!!.complete(Unit)

        assertEquals(
            listOf<Pair<EventType, String?>>(EventType.SUBSTITUTION_OUT to "p6", EventType.SUBSTITUTION_IN to "p8", EventType.SUBSTITUTION_OUT to "p7", EventType.SUBSTITUTION_IN to "p9"),
            server.eventsSent
        )
        // Each player coming on takes the place of the one they replaced.
        val onPitch = (startSlots.values - "p6" - "p7" + "p8" + "p9").toSet()
        val slots = idle.details.currentSlots(idle.events, onPitch)
        assertEquals("p8", slots[5])
        assertEquals("p9", slots[6])
    }

    @Test
    fun aRefusedSaveShowsTheServersPitchNotAnOldCopy() = runTest {
        viewModel.load("m1")
        val first = swap(1, 2)
        server.lineupGate = CompletableDeferred()
        swap(3, 4)
        swap(5, 6)
        server.lineupGate!!.completeExceptionally(IllegalStateException("refused"))

        // The server only has the first swap, and that's what the pitch shows now. (The error line's
        // text comes from compose resources, which a JVM unit test can't load.)
        assertEquals(first, shown)
    }
}
