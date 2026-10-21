package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ManageCallUpRequest
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.exception.ValidationException
import kotlinx.datetime.LocalDate
import io.mockk.coVerify
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
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
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = ManageCallUpsUseCase(matchRepository, transactionManager, checkUserPermissionUseCase, playerRepository)

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
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns TeamRole.STAFF
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
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
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
