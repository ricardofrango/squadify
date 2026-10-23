package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.LiveMatchUpdate
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.MatchLineup
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SetMatchLineupRequest
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
    private val useCase = SetMatchLineupUseCase(matchRepository, matchEventRepository, teamRepository, liveMatchBus, checkUserPermissionUseCase, transactionManager)

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val scheduled = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1))
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
