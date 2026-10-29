package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.LiveMatchUpdate
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchLineup
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.UpdateMatchRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import com.ricardofrango.squadify.model.defaultFormation
import com.ricardofrango.squadify.model.formationText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class UpdateMatchUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
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
    private val notifyMatchEventUseCase = mockk<NotifyMatchEventUseCase>(relaxed = true)
    private val clubMatchesBus = mockk<ClubMatchesBus>(relaxed = true)
    private val clubRepository = mockk<ClubRepository>().also {
        coEvery { it.getClubById(any()) } returns Club("club-1", "FC", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)
    }
    private val matchEventRepository = mockk<MatchEventRepository>().also {
        coEvery { it.getScoreByMatch(any()) } returns MatchScore("m-1", 0, 0)
    }
    private val liveMatchBus = mockk<LiveMatchBus>(relaxed = true)
    private val useCase = UpdateMatchUseCase(
        matchRepository, opponentRepository, ClubAccessPolicy(checkUserPermissionUseCase), notifyMatchEventUseCase, clubMatchesBus, transactionManager,
        clubRepository, matchEventRepository, liveMatchBus
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val match = Match(matchId, clubId, teamId, "opp-1", "Old field", Instant.fromEpochMilliseconds(1), notes = "old", teamSize = 7)

    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    @Test
    fun coachMergesOnlyTheProvidedFields() = runBlocking {
        val coach = user("coach")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.squadify.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        val saved = slot<Match>()
        coEvery { matchRepository.updateMatch(capture(saved)) } returns true

        useCase(coach.id, matchId, UpdateMatchRequest(location = "New field"))

        assertEquals("New field", saved.captured.location)
        assertEquals("old", saved.captured.notes) // untouched
        assertEquals(match.venue, saved.captured.venue) // untouched
        assertEquals("opp-1", saved.captured.opponentId) // untouched
        coVerify { notifyMatchEventUseCase(matchId, MatchNotificationEvent.RESCHEDULED) }
        coVerify { clubMatchesBus.publish(clubId, com.ricardofrango.squadify.model.ClubMatchesUpdate(match = saved.captured)) }
    }

    @Test
    fun editingOnlyNotesDoesNotNotify() = runBlocking {
        val coach = user("coach")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.squadify.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        coEvery { matchRepository.updateMatch(any()) } returns true

        useCase(coach.id, matchId, UpdateMatchRequest(notes = "new notes"))

        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any()) }
    }

    @Test
    fun rejectsAnOpponentFromAnotherClub() = runBlocking {
        val coach = user("coach")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.squadify.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        coEvery { opponentRepository.getOpponentById("opp-other") } returns Opponent("opp-other", "other-club", "X", null, null)

        assertFailsWith<ForbiddenException> {
            useCase(coach.id, matchId, UpdateMatchRequest(opponentId = "opp-other"))
        }
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any()) }
    }

    @Test
    fun plainMemberIsDenied() = runBlocking {
        val member = user("member")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.squadify.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(member.id, matchId, UpdateMatchRequest(notes = "x"))
        }
        Unit
    }

    @Test
    fun theMatchTypeCanBeChangedWithoutNotifyingTheClub() = runBlocking {
        val coach = user("coach")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.squadify.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        val updated = slot<Match>()
        coEvery { matchRepository.updateMatch(capture(updated)) } returns true

        useCase(coach.id, matchId, UpdateMatchRequest(type = MatchType.TOURNAMENT))

        assertEquals(MatchType.TOURNAMENT, updated.captured.type)
        // The type isn't a schedule change (opponent/date/location), so no push goes out.
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any()) }
    }

    @Test
    fun finishedOrCancelledMatchesCannotBeEdited() = runBlocking {
        val coach = user("coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.squadify.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)

        for (status in listOf(MatchStatus.FINISHED, MatchStatus.CANCELLED)) {
            coEvery { matchRepository.getMatchById(matchId) } returns match.copy(status = status)
            assertFailsWith<ConflictException> {
                useCase(coach.id, matchId, UpdateMatchRequest(type = MatchType.FRIENDLY))
            }
        }
        coVerify(exactly = 0) { matchRepository.updateMatch(any()) }
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any()) }
    }

    private fun asCoach(m: Match): Unit = runBlocking {
        val coach = user("coach")
        coEvery { matchRepository.getMatchById(matchId) } returns m
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.squadify.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        coEvery { matchRepository.updateMatch(any()) } returns true
    }

    @Test
    fun theHalvesCanBeChangedBeforeKickoff() = runBlocking {
        asCoach(match)
        val saved = slot<Match>()
        coEvery { matchRepository.updateMatch(capture(saved)) } returns true
        useCase("coach", matchId, UpdateMatchRequest(halves = 1))
        assertEquals(1, saved.captured.halves)
    }

    @Test
    fun theHalvesAreFixedOncePlayStarts() = runBlocking {
        asCoach(match.copy(status = com.ricardofrango.squadify.model.MatchStatus.LIVE))
        assertFailsWith<ConflictException> { useCase("coach", matchId, UpdateMatchRequest(halves = 1)) }
        // Sending the unchanged value (the edit sheet always sends the whole form) is fine.
        useCase("coach", matchId, UpdateMatchRequest(halves = 2, notes = "n"))
        Unit
    }

    @Test
    fun theVenueCanBeChanged() = runBlocking {
        val coach = user("coach")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.squadify.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        val saved = slot<Match>()
        coEvery { matchRepository.updateMatch(capture(saved)) } returns true

        useCase(coach.id, matchId, UpdateMatchRequest(venue = com.ricardofrango.squadify.model.MatchVenue.NEUTRAL))

        assertEquals(com.ricardofrango.squadify.model.MatchVenue.NEUTRAL, saved.captured.venue)
    }

    private fun coachOfA7ASideTeam(): User {
        val coach = user("coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.squadify.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true, teamSize = 7)
        return coach
    }

    private fun placed(playerId: String, slot: Int) = MatchCallUpWithPlayer(
        MatchCallUp("cu-$playerId", matchId, playerId, CallUpStatus.CALLED_UP, pitchSlot = slot),
        Player(playerId, clubId, "F", "L", birthDate = kotlinx.datetime.LocalDate(2015, 1, 1), active = true)
    )

    @Test
    fun beforeKickoffTheTeamSizeChangesAndThePitchFollows() = runBlocking {
        val coach = coachOfA7ASideTeam()
        coEvery { matchRepository.getMatchById(matchId) } returns match
        val saved = slot<Match>()
        coEvery { matchRepository.updateMatch(capture(saved)) } returns true
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns listOf(placed("p1", 0), placed("p2", 4), placed("p3", 6))
        coEvery { matchRepository.setLineup(any(), any(), any()) } returns true

        useCase(coach.id, matchId, UpdateMatchRequest(teamSize = 5))

        assertEquals(5, saved.captured.teamSize)
        // The new size's default formation, keeping whoever stands in a position that still exists.
        val lineup = MatchLineup(defaultFormation(5).formationText(), mapOf(0 to "p1", 4 to "p2"))
        coVerify { matchRepository.setLineup(matchId, lineup.formation, lineup.slots) }
        coVerify { liveMatchBus.publish(matchId, LiveMatchUpdate(score = MatchScore("m-1", 0, 0), match = saved.captured.copy(formation = lineup.formation), lineup = lineup)) }
    }

    @Test
    fun aSizeTheSportIsntPlayedWithIsRefused() = runBlocking {
        val coach = coachOfA7ASideTeam()
        coEvery { matchRepository.getMatchById(matchId) } returns match
        assertFailsWith<ValidationException> { useCase(coach.id, matchId, UpdateMatchRequest(teamSize = 6)) }
        Unit
    }

    @Test
    fun aLiveMatchKeepsItsTeamSize() = runBlocking {
        val coach = coachOfA7ASideTeam()
        coEvery { matchRepository.getMatchById(matchId) } returns
            match.copy(status = MatchStatus.LIVE, kickoffAt = Instant.fromEpochMilliseconds(1))
        coEvery { matchRepository.updateMatch(any()) } returns true

        assertFailsWith<ConflictException> { useCase(coach.id, matchId, UpdateMatchRequest(teamSize = 5)) }
        // Sending the size it already has is no change.
        useCase(coach.id, matchId, UpdateMatchRequest(teamSize = 7, notes = "n"))
        coVerify(exactly = 0) { matchRepository.setLineup(any(), any(), any()) }
    }
}
