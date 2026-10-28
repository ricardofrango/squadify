package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.LiveMatchUpdate
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchLineup
import com.ricardofrango.squadify.model.MatchLineupChange
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SetMatchLineupRequest
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.Team
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

class SetMatchLineupUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val liveMatchBus = mockk<LiveMatchBus>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    // The match clock: 12 minutes and 30 seconds after a kickoff at 1 ms.
    private val now = Instant.fromEpochMilliseconds(1 + 750_000)
    private val clock = object : kotlin.time.Clock { override fun now() = this@SetMatchLineupUseCaseTest.now }
    private val clubRepository = mockk<ClubRepository>().also {
        coEvery { it.getClubById(any()) } returns Club("club-1", "FC", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)
    }
    private val useCase = SetMatchLineupUseCase(matchRepository, matchEventRepository, liveMatchBus, checkUserPermissionUseCase, transactionManager, clubRepository, clock)

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val scheduled = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1), teamSize = 7)
    private val coach = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0))
    private val ids = (1..8).map { "p$it" }

    private fun callUp(playerId: String, starting: Boolean = false) = MatchCallUpWithPlayer(
        MatchCallUp("cu-$playerId", matchId, playerId, CallUpStatus.CALLED_UP, isStarting = starting),
        Player(playerId, clubId, "F", "L", birthDate = kotlinx.datetime.LocalDate(2015, 1, 1), active = true)
    )

    private fun stub(match: Match, starting: Set<String> = emptySet()) {
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns Team(teamId, clubId, ag1, "s-1", "Sub-13 A", active = true, teamSize = 7)
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns ids.map { callUp(it, it in starting) }
        coEvery { matchRepository.setLineup(any(), any(), any()) } returns true
        coEvery { matchRepository.addLineupChange(any(), any()) } returns Unit
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)
    }

    @Test
    fun beforeKickoffTheCoachPlacesCalledUpPlayersAndEveryDeviceHearsIt() = runBlocking {
        stub(scheduled)
        // Not every position filled yet: that's fine before kickoff.
        val result = useCase(coach.id, matchId, SetMatchLineupRequest("2-3-1", mapOf(0 to "p1", 3 to "p2")))

        assertEquals(MatchLineup("2-3-1", mapOf(0 to "p1", 3 to "p2")), result)
        coVerify { matchRepository.setLineup(matchId, "2-3-1", mapOf(0 to "p1", 3 to "p2")) }
        coVerify { liveMatchBus.publish(matchId, LiveMatchUpdate(score = MatchScore(matchId, 0, 0), match = scheduled.copy(formation = "2-3-1"), lineup = result)) }
        // Placing the starters isn't part of the game's timeline (kickoff keeps the side that starts).
        coVerify(exactly = 0) { matchRepository.addLineupChange(any(), any()) }
    }

    @Test
    fun duringTheGameEachArrangementIsKeptAtTheMatchClocksMinute() = runBlocking {
        val starters = ids.take(7).toSet()
        val slots = ids.take(7).mapIndexed { i, id -> i to id }.toMap()
        coEvery { matchEventRepository.getEventsByMatch(matchId) } returns emptyList()

        val live = scheduled.copy(status = MatchStatus.LIVE, kickoffAt = Instant.fromEpochMilliseconds(1), halfLengthMinutes = 30)
        stub(live, starters)
        useCase(coach.id, matchId, SetMatchLineupRequest("3-2-1", slots))
        coVerify { matchRepository.addLineupChange(matchId, MatchLineupChange(12, 1, "3-2-1", slots, now)) }

        // At half time: the minute the first half ended.
        stub(live.copy(status = MatchStatus.HALF_TIME, firstHalfEndMinute = 32), starters)
        useCase(coach.id, matchId, SetMatchLineupRequest("2-3-1", slots))
        coVerify { matchRepository.addLineupChange(matchId, MatchLineupChange(32, 1, "2-3-1", slots, now)) }

        // In the second half (restarted 1 minute ago, the clock back at 30'): period 2.
        stub(live.copy(secondHalfStartedAt = Instant.fromEpochMilliseconds(1 + 690_000), firstHalfEndMinute = 32), starters)
        useCase(coach.id, matchId, SetMatchLineupRequest("2-3-1", slots))
        coVerify { matchRepository.addLineupChange(matchId, MatchLineupChange(31, 2, "2-3-1", slots, now)) }
    }

    @Test
    fun beforeKickoffTheMatchCanBePlayedWithAnotherTeamSize() = runBlocking {
        stub(scheduled)
        coEvery { matchRepository.updateMatch(any()) } returns true
        // The 7-a-side team plays this one 5-a-side: the formation and positions are for 5.
        val result = useCase(coach.id, matchId, SetMatchLineupRequest("2-1-1", mapOf(0 to "p1", 4 to "p2"), teamSize = 5))

        assertEquals(MatchLineup("2-1-1", mapOf(0 to "p1", 4 to "p2")), result)
        coVerify { matchRepository.updateMatch(scheduled.copy(teamSize = 5)) }
        coVerify { liveMatchBus.publish(matchId, LiveMatchUpdate(score = MatchScore(matchId, 0, 0), match = scheduled.copy(teamSize = 5, formation = "2-1-1"), lineup = result)) }
        // A position past the new count, or a size the sport isn't played with, is refused.
        assertFailsWith<ValidationException> { useCase(coach.id, matchId, SetMatchLineupRequest("2-1-1", mapOf(5 to "p1"), teamSize = 5)) }
        assertFailsWith<ValidationException> { useCase(coach.id, matchId, SetMatchLineupRequest("2-2-1", emptyMap(), teamSize = 6)) }

        // And back to 7: the count is always stored on the match.
        stub(scheduled.copy(teamSize = 5))
        useCase(coach.id, matchId, SetMatchLineupRequest("2-3-1", emptyMap(), teamSize = 7))
        coVerify { matchRepository.updateMatch(scheduled) }
    }

    @Test
    fun onceTheGameIsOnTheTeamSizeIsFixed() = runBlocking {
        stub(scheduled.copy(status = MatchStatus.LIVE, kickoffAt = Instant.fromEpochMilliseconds(1), halfLengthMinutes = 30), ids.take(7).toSet())
        coEvery { matchEventRepository.getEventsByMatch(matchId) } returns emptyList()
        assertFailsWith<ConflictException> { useCase(coach.id, matchId, SetMatchLineupRequest("2-1-1", emptyMap(), teamSize = 5)) }
        // The same count it kicked off with is no change.
        useCase(coach.id, matchId, SetMatchLineupRequest("2-3-1", emptyMap(), teamSize = 7))
        Unit
    }

    @Test
    fun aBadFormationPositionOrPlayerIsRefused() = runBlocking {
        stub(scheduled)
        assertFailsWith<ValidationException> { useCase(coach.id, matchId, SetMatchLineupRequest("2-3-2", emptyMap())) }
        assertFailsWith<ValidationException> { useCase(coach.id, matchId, SetMatchLineupRequest("2-3-1", mapOf(7 to "p1"))) }
        assertFailsWith<ValidationException> { useCase(coach.id, matchId, SetMatchLineupRequest("2-3-1", mapOf(0 to "p1", 1 to "p1"))) }
        assertFailsWith<ValidationException> { useCase(coach.id, matchId, SetMatchLineupRequest("2-3-1", mapOf(0 to "stranger"))) }
        stub(scheduled.copy(status = MatchStatus.FINISHED))
        assertFailsWith<ValidationException> { useCase(coach.id, matchId, SetMatchLineupRequest("2-3-1", emptyMap())) }
        coVerify(exactly = 0) { matchRepository.setLineup(any(), any(), any()) }
    }

    @Test
    fun duringTheGameOnlyPlayersOnThePitchCanBePlaced() = runBlocking {
        val live = scheduled.copy(status = MatchStatus.LIVE, kickoffAt = Instant.fromEpochMilliseconds(1), halfLengthMinutes = 30)
        val starters = ids.take(7).toSet()
        stub(live, starters)
        // p7 came off for p8 at 10'.
        coEvery { matchEventRepository.getEventsByMatch(matchId) } returns listOf(
            MatchEvent("e1", matchId, EventType.SUBSTITUTION_OUT, 10, playerId = "p7", createdAt = Instant.fromEpochMilliseconds(2), period = 1),
            MatchEvent("e2", matchId, EventType.SUBSTITUTION_IN, 10, playerId = "p8", createdAt = Instant.fromEpochMilliseconds(3), period = 1)
        )
        // The coach swaps p2 and p8 into each other's places, in a new formation.
        val slots = mapOf(0 to "p1", 1 to "p8", 2 to "p3", 3 to "p4", 4 to "p5", 5 to "p6", 6 to "p2")
        assertEquals("3-2-1", useCase(coach.id, matchId, SetMatchLineupRequest("3-2-1", slots)).formation)

        // p7 is off the pitch now.
        assertFailsWith<ValidationException> {
            useCase(coach.id, matchId, SetMatchLineupRequest("2-3-1", slots + (6 to "p7")))
        }
        Unit
    }
}
