package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.AgeGroupRole
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
    private val useCase = CreateMatchUseCase(
        matchRepository, teamRepository, opponentRepository, checkUserPermissionUseCase, notifyMatchEventUseCase, clubMatchesBus, transactionManager
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

        coVerify { notifyMatchEventUseCase(result.id, MatchNotificationEvent.SCHEDULED) }
        coVerify { clubMatchesBus.publish(clubId, com.ricardofrango.squadify.model.ClubMatchesUpdate(match = result)) }
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
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any()) }
    }

    @Test
    fun aNonexistentTeamIsNotFound() = runBlocking {
        coEvery { teamRepository.getTeamById("no-such-team") } returns null

        assertFailsWith<NotFoundException> {
            useCase("anyone", CreateMatchRequest(teamId = "no-such-team", opponentId = "opp-1", dateTime = Instant.fromEpochMilliseconds(1), type = MatchType.CHAMPIONSHIP))
        }
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any()) }
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
