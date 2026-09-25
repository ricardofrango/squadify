package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.LiveMatchBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.StartMatchRequest
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamRole
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

class StartMatchUseCaseTest {

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
    private val notifyMatchEventUseCase = mockk<NotifyMatchEventUseCase>(relaxed = true)
    private val clubMatchesBus = mockk<ClubMatchesBus>(relaxed = true)
    private val useCase = StartMatchUseCase(
        matchRepository,
        matchEventRepository,
        teamRepository,
        liveMatchBus,
        checkUserPermissionUseCase,
        notifyMatchEventUseCase,
        clubMatchesBus,
        transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val match = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1))
    private val team7aside = Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true, teamSize = 7)

    private fun coach() = User("coach", "coach@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    private fun callUp(playerId: String, status: CallUpStatus = CallUpStatus.CALLED_UP) =
        MatchCallUpWithPlayer(
            callUp = MatchCallUp("cu-$playerId", matchId, playerId, status),
            player = Player(playerId, clubId, "First", "Last", birthDate = kotlinx.datetime.LocalDate(2015, 1, 1), shirtNumber = null, photoUrl = null, active = true)
        )

    private fun stubCoachPermission(coach: User) {
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns team7aside
    }

    @Test
    fun kicksOffWithAValidSevenASideLineup() = runBlocking {
        val coach = coach()
        coEvery { matchRepository.getMatchById(matchId) } returns match
        stubCoachPermission(coach)
        val calledUpIds = (1..8).map { "p$it" }
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns calledUpIds.map { callUp(it) }
        coEvery { matchRepository.startMatch(matchId, any(), 30) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val starting = calledUpIds.take(7)
        val result = useCase(coach.id, matchId, StartMatchRequest(starting, 30))

        assertEquals(true, result)
        coVerify { matchRepository.startMatch(matchId, starting, 30) }
        coVerify { notifyMatchEventUseCase(matchId, MatchNotificationEvent.KICKOFF) }
        coVerify { clubMatchesBus.publish(clubId, com.ricardofrango.squadify.model.ClubMatchesUpdate(match = match)) }
    }

    @Test
    fun rejectsALineupThatDoesNotMatchTeamSize() = runBlocking {
        val coach = coach()
        coEvery { matchRepository.getMatchById(matchId) } returns match
        stubCoachPermission(coach)
        val calledUpIds = (1..8).map { "p$it" }
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns calledUpIds.map { callUp(it) }

        assertFailsWith<ValidationException> {
            useCase(coach.id, matchId, StartMatchRequest(calledUpIds.take(5), 30))
        }
        coVerify(exactly = 0) { notifyMatchEventUseCase(any(), any()) }
    }

    @Test
    fun rejectsAPlayerWhoIsNotCalledUp() = runBlocking {
        val coach = coach()
        coEvery { matchRepository.getMatchById(matchId) } returns match
        stubCoachPermission(coach)
        val calledUpIds = (1..7).map { "p$it" }
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns calledUpIds.map { callUp(it) }

        assertFailsWith<ValidationException> {
            useCase(coach.id, matchId, StartMatchRequest(calledUpIds.dropLast(1) + "not-called-up", 30))
        }
        Unit
    }

    @Test
    fun rejectsStartingAMatchThatAlreadyStarted() = runBlocking {
        val coach = coach()
        val liveMatch = match.copy(status = MatchStatus.LIVE)
        coEvery { matchRepository.getMatchById(matchId) } returns liveMatch
        stubCoachPermission(coach)

        assertFailsWith<ValidationException> {
            useCase(coach.id, matchId, StartMatchRequest((1..7).map { "p$it" }, 30))
        }
        Unit
    }

    @Test
    fun ageGroupStaffCanStartAMatch() = runBlocking {
        val staff = coach()
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns TeamRole.STAFF
        coEvery { teamRepository.getTeamById(teamId) } returns team7aside
        val calledUpIds = (1..7).map { "p$it" }
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns calledUpIds.map { callUp(it) }
        coEvery { matchRepository.startMatch(matchId, calledUpIds, 30) } returns true
        coEvery { matchEventRepository.getScoreByMatch(matchId) } returns MatchScore(matchId, 0, 0)

        val result = useCase(staff.id, matchId, StartMatchRequest(calledUpIds, 30))

        assertEquals(true, result)
    }

    @Test
    fun plainMemberIsDenied() = runBlocking {
        val member = User("member", "member@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamById(teamId) } returns team7aside
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(member.id, matchId, StartMatchRequest((1..7).map { "p$it" }, 30))
        }
        Unit
    }
}
