package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.ClubAccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.ClubMatchesBus
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.Club
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.ClubStatus
import com.sportssoftw.squadcall.model.CreateMatchRequest
import com.sportssoftw.squadcall.model.Match
import com.sportssoftw.squadcall.model.MatchType
import com.sportssoftw.squadcall.model.MatchVenue
import com.sportssoftw.squadcall.model.Opponent
import com.sportssoftw.squadcall.model.SportType
import com.sportssoftw.squadcall.model.Team
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class CreateMatchUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
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
    private val useCase = CreateMatchUseCase(
        matchRepository, teamRepository, opponentRepository, ClubAccessPolicy(checkUserPermissionUseCase), notifyMatchEventUseCase, clubMatchesBus, transactionManager, clubRepository
    )

    private val clubId = "club-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val team = Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)
    private val opponent = Opponent("opp-1", clubId, "Rivals FC")

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    @Test
    fun creatingAMatchNotifiesTheClub() = runBlocking {
        val coach = coach()
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { opponentRepository.getOpponentById("opp-1") } returns opponent
        coEvery { matchRepository.createMatch(any()) } answers { firstArg() }

        val result = useCase(coach.id, CreateMatchRequest(teamId = teamId, opponentId = "opp-1", dateTime = Instant.fromEpochMilliseconds(1), type = MatchType.CHAMPIONSHIP))

        assertEquals(MatchType.CHAMPIONSHIP, result.type, "the chosen type is stored on the match")

        coVerify { notifyMatchEventUseCase(result.id, MatchNotificationEvent.SCHEDULED, causedBy = any()) }
        coVerify { clubMatchesBus.publish(clubId, com.sportssoftw.squadcall.model.ClubMatchesUpdate(match = result)) }
    }

    @Test
    fun aMatchCanBePlayedWithAnotherTeamSizeThanItsTeams() = runBlocking {
        val coach = coach()
        coEvery { teamRepository.getTeamById(teamId) } returns team.copy(teamSize = 9)
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { opponentRepository.getOpponentById("opp-1") } returns opponent
        coEvery { matchRepository.createMatch(any()) } answers { firstArg() }
        fun request(size: Int) = CreateMatchRequest(teamId = teamId, opponentId = "opp-1", dateTime = Instant.fromEpochMilliseconds(1), type = MatchType.FRIENDLY, teamSize = size)

        assertEquals(7, useCase(coach.id, request(7)).teamSize)
        assertFailsWith<ValidationException> { useCase(coach.id, request(6)) }
        // Without one picked, the team's count -- stored on the match, so a later change to the team
        // doesn't touch it.
        assertEquals(9, useCase(coach.id, request(7).copy(teamSize = null)).teamSize)
        coEvery { teamRepository.getTeamById(teamId) } returns team.copy(teamSize = null)
        assertEquals(11, useCase(coach.id, request(7).copy(teamSize = null)).teamSize)
        Unit
    }

    @Test
    fun rejectsAnOpponentFromAnotherClub() = runBlocking {
        val coach = coach()
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { opponentRepository.getOpponentById("opp-other") } returns Opponent("opp-other", "other-club", "X")

        assertFailsWith<ForbiddenException> {
            useCase(coach.id, CreateMatchRequest(teamId = teamId, opponentId = "opp-other", dateTime = Instant.fromEpochMilliseconds(1), type = MatchType.CHAMPIONSHIP))
        }
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun aNonexistentTeamIsNotFound() = runBlocking {
        coEvery { teamRepository.getTeamById("no-such-team") } returns null

        assertFailsWith<NotFoundException> {
            useCase("anyone", CreateMatchRequest(teamId = "no-such-team", opponentId = "opp-1", dateTime = Instant.fromEpochMilliseconds(1), type = MatchType.CHAMPIONSHIP))
        }
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any(), any(), any()) }
    }

    @Test
    fun aMatchIsTwoHalvesByDefaultOrOneWhenChosen() = runBlocking {
        val coach = coach()
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { opponentRepository.getOpponentById("opp-1") } returns opponent
        coEvery { matchRepository.createMatch(any()) } answers { firstArg() }

        val twoHalves = useCase(coach.id, CreateMatchRequest(teamId = teamId, opponentId = "opp-1", dateTime = Instant.fromEpochMilliseconds(1), type = MatchType.FRIENDLY))
        assertEquals(2, twoHalves.halves)
        val oneHalf = useCase(coach.id, CreateMatchRequest(teamId = teamId, opponentId = "opp-1", dateTime = Instant.fromEpochMilliseconds(1), type = MatchType.FRIENDLY, halves = 1))
        assertEquals(1, oneHalf.halves)
        assertFailsWith<ValidationException> {
            useCase(coach.id, CreateMatchRequest(teamId = teamId, opponentId = "opp-1", dateTime = Instant.fromEpochMilliseconds(1), type = MatchType.FRIENDLY, halves = 3))
        }
        Unit
    }

    @Test
    fun theVenueIsSavedAndAnOldAppsMatchIsAtHome() = runBlocking {
        val coach = coach()
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { opponentRepository.getOpponentById("opp-1") } returns opponent
        coEvery { matchRepository.createMatch(any()) } answers { firstArg() }

        val away = useCase(coach.id, CreateMatchRequest(teamId = teamId, opponentId = "opp-1", dateTime = Instant.fromEpochMilliseconds(1), type = MatchType.FRIENDLY, venue = MatchVenue.AWAY))
        assertEquals(MatchVenue.AWAY, away.venue)
        // An app from before the venue sends none: the match is at home.
        val old = useCase(coach.id, CreateMatchRequest(teamId = teamId, opponentId = "opp-1", dateTime = Instant.fromEpochMilliseconds(1), type = MatchType.FRIENDLY))
        assertEquals(MatchVenue.HOME, old.venue)
    }
}
