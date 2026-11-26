package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.FcmTokenRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.service.ClubPushAudience
import com.sportssoftw.squadcall.domain.service.PushNotificationService
import com.sportssoftw.squadcall.domain.access.ClubAccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.ManageCallUpRequest
import com.sportssoftw.squadcall.model.Match
import com.sportssoftw.squadcall.model.CallUpStatus
import com.sportssoftw.squadcall.model.MatchCallUp
import com.sportssoftw.squadcall.model.MatchCallUpWithPlayer
import com.sportssoftw.squadcall.model.Player
import com.sportssoftw.squadcall.exception.ValidationException
import kotlinx.datetime.LocalDate
import io.mockk.coVerify
import com.sportssoftw.squadcall.model.Team
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class ManageCallUpsUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
    private val fcmTokenRepository = mockk<FcmTokenRepository>()
    private val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = ManageCallUpsUseCase(
        matchRepository, transactionManager, ClubAccessPolicy(checkUserPermissionUseCase), playerRepository,
        teamRepository, opponentRepository, fcmTokenRepository, pushNotificationService, ClubPushAudience(mockk(), ageGroupRepository)
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"
    private val match = Match(matchId, clubId, teamId, "opp-1", dateTime = Instant.fromEpochMilliseconds(1))
    private val team = Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", active = true)

    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    @Test
    fun ageGroupStaffIsDeniedByDefault() = runBlocking {
        // MANAGE_CALL_UPS defaults to COACH (unlike MANAGE_LIVE_MATCH, which stays STAFF) -- call-ups
        // are a pre-match action, kept as its own, stricter-by-default setting from everything that
        // happens once the match goes live.
        val staff = user("staff")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns AgeGroupRole.STAFF
        coEvery { teamRepository.getTeamById(teamId) } returns team

        assertFailsWith<ForbiddenException> {
            useCase(staff.id, matchId, ManageCallUpRequest(listOf("p1", "p2")))
        }
        Unit
    }

    @Test
    fun ageGroupCoachCanManageCallUps() = runBlocking {
        val coach = user("coach")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { matchRepository.manageCallUps(matchId, listOf("p1")) } returns true
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns emptyList()
        coEvery { playerRepository.getPlayersData(listOf("p1")) } returns listOf(pl("p1"))

        val result = useCase(coach.id, matchId, ManageCallUpRequest(listOf("p1")))

        assertEquals(true, result)
    }

    @Test
    fun plainMemberIsDenied() = runBlocking {
        val member = user("member")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(member.id, matchId, ManageCallUpRequest(listOf("p1")))
        }
        Unit
    }

    private fun pl(id: String, active: Boolean = true) = Player(id, clubId, "P", id, LocalDate(2012, 1, 1), active = active)

    private fun coordinator(): String {
        val coord = user("coord")
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { userRepository.findById(coord.id) } returns coord
        coEvery { userRepository.getUserRoleInClub(coord.id, clubId) } returns ClubRole.COORDINATOR
        return coord.id
    }

    @Test
    fun aDisabledPlayerCantBeNewlyCalledUp() = runBlocking<Unit> {
        val coord = coordinator()
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns emptyList()
        coEvery { playerRepository.getPlayersData(listOf("p1", "p2")) } returns listOf(pl("p1"), pl("p2", active = false))

        assertFailsWith<ValidationException> { useCase(coord, matchId, ManageCallUpRequest(listOf("p1", "p2"))) }
        coVerify(exactly = 0) { matchRepository.manageCallUps(any(), any()) }
    }

    @Test
    fun aDisabledPlayerAlreadyInTheCallUpCanStay() = runBlocking {
        val coord = coordinator()
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns
            listOf(MatchCallUpWithPlayer(MatchCallUp("c1", matchId, "p2", CallUpStatus.CALLED_UP), pl("p2", active = false)))
        coEvery { playerRepository.getPlayersData(listOf("p1")) } returns listOf(pl("p1"))
        coEvery { matchRepository.manageCallUps(matchId, listOf("p1", "p2")) } returns true

        assertEquals(true, useCase(coord, matchId, ManageCallUpRequest(listOf("p1", "p2"))))
    }
}
