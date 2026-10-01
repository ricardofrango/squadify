package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UpdateMatchRequest
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
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
    private val useCase = UpdateMatchUseCase(
        matchRepository, opponentRepository, checkUserPermissionUseCase, notifyMatchEventUseCase, clubMatchesBus, transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val match = Match(matchId, clubId, teamId, "opp-1", "Old field", Instant.fromEpochMilliseconds(1), notes = "old")

    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    @Test
    fun coachMergesOnlyTheProvidedFields() = runBlocking {
        val coach = user("coach")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.squadify.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        val saved = slot<Match>()
        coEvery { matchRepository.updateMatch(capture(saved)) } returns true

        useCase(coach.id, matchId, UpdateMatchRequest(location = "New field"))

        assertEquals("New field", saved.captured.location)
        assertEquals("old", saved.captured.notes) // untouched
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
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
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
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
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
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
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
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
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
}
