package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.MatchRepository
import com.ricardofrango.clubmanager.domain.repository.OpponentRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ForbiddenException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.Match
import com.ricardofrango.clubmanager.model.Opponent
import com.ricardofrango.clubmanager.model.TeamRole
import com.ricardofrango.clubmanager.model.UpdateMatchRequest
import com.ricardofrango.clubmanager.model.User
import com.ricardofrango.clubmanager.model.UserStatus
import io.mockk.coEvery
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
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager
    )
    private val useCase = UpdateMatchUseCase(matchRepository, opponentRepository, checkUserPermissionUseCase, transactionManager)

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
            com.ricardofrango.clubmanager.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        val saved = slot<Match>()
        coEvery { matchRepository.updateMatch(capture(saved)) } returns true

        useCase(coach.id, matchId, UpdateMatchRequest(location = "New field"))

        assertEquals("New field", saved.captured.location)
        assertEquals("old", saved.captured.notes) // untouched
        assertEquals("opp-1", saved.captured.opponentId) // untouched
    }

    @Test
    fun rejectsAnOpponentFromAnotherClub() = runBlocking {
        val coach = user("coach")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.clubmanager.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        coEvery { opponentRepository.getOpponentById("opp-other") } returns Opponent("opp-other", "other-club", "X", null, null)

        assertFailsWith<ForbiddenException> {
            useCase(coach.id, matchId, UpdateMatchRequest(opponentId = "opp-other"))
        }
        Unit
    }

    @Test
    fun plainMemberIsDenied() = runBlocking {
        val member = user("member")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamById(teamId) } returns
            com.ricardofrango.clubmanager.model.Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(member.id, matchId, UpdateMatchRequest(notes = "x"))
        }
        Unit
    }
}
