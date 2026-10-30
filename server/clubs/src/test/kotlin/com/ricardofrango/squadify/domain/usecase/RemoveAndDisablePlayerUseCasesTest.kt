package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.CallUpStatus
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchCallUp
import com.ricardofrango.squadify.model.MatchCallUpWithPlayer
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

// Removing a player from an age group (not the club), disabling a player in the club, and reactivating them.
class RemoveAndDisablePlayerUseCasesTest {

    private val playerRepository = mockk<PlayerRepository>(relaxed = true)
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val matchRepository = mockk<MatchRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val check = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository)
    private val remove = RemovePlayerFromAgeGroupUseCase(playerRepository, ageGroupRepository, teamRepository, matchRepository, ClubAccessPolicy(check), transactionManager)
    private val disable = DisablePlayerUseCase(playerRepository, teamRepository, matchRepository, check, transactionManager)
    private val reactivate = ReactivatePlayerUseCase(playerRepository, check, transactionManager)
    private val add = AddPlayersToAgeGroupUseCase(playerRepository, ageGroupRepository, ClubAccessPolicy(check), transactionManager)

    private val clubId = "club-1"
    private val ag = AgeGroup("ag-1", clubId, "Sub-15", "s-1", 2012, 2012)
    private val other = AgeGroup("ag-2", clubId, "Sub-17", "s-1", 2010, 2010)
    private val player = Player("p-1", clubId, "Tomás", "Bragança", LocalDate(2012, 3, 1), active = true)
    private val teamA = Team("t-a", clubId, ag.id, "s-1", "Sub-15 A", active = true)
    private val teamOther = Team("t-o", clubId, other.id, "s-1", "Sub-17 A", active = true)
    private val upcoming = Match("m-up", clubId, teamA.id, "opp", dateTime = Instant.fromEpochMilliseconds(2))
    private val played = Match("m-played", clubId, teamA.id, "opp", dateTime = Instant.fromEpochMilliseconds(1), status = MatchStatus.FINISHED)

    private fun callUp(matchId: String, p: Player) = MatchCallUpWithPlayer(MatchCallUp("c-$matchId-${p.id}", matchId, p.id, CallUpStatus.CALLED_UP), p)
    private val teammate = player.copy(id = "p-2", firstName = "Gil")

    private fun user(id: String, clubRole: ClubRole, teamRole: AgeGroupRole? = null): String {
        coEvery { userRepository.findById(id) } returns User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.getUserRoleInClub(id, clubId) } returns clubRole
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(id, any()) } returns teamRole
        return id
    }

    init {
        coEvery { ageGroupRepository.getAgeGroupById(ag.id) } returns ag
        coEvery { ageGroupRepository.getAgeGroupById(other.id) } returns other
        coEvery { playerRepository.getPlayerById(player.id) } returns player
        coEvery { playerRepository.removePlayerFromAgeGroup(ag.id, player.id) } returns true
        coEvery { teamRepository.getTeamsByClub(clubId) } returns listOf(teamA, teamOther)
        coEvery { matchRepository.getMatchesByTeam(teamA.id) } returns listOf(upcoming, played)
        coEvery { matchRepository.getMatchesByTeam(teamOther.id) } returns emptyList()
        coEvery { matchRepository.getCallUpsForMatch(upcoming.id) } returns listOf(callUp(upcoming.id, player), callUp(upcoming.id, teammate))
        coEvery { matchRepository.getCallUpsForMatch(played.id) } returns listOf(callUp(played.id, player))
    }

    @Test
    fun theAgeGroupsCoachRemovesAPlayerFromThePoolAndUpcomingCallUpsOnly() = runBlocking {
        remove(user("coach", ClubRole.MEMBER, AgeGroupRole.COACH), ag.id, player.id)

        coVerify { playerRepository.removePlayerFromAgeGroup(ag.id, player.id) }
        // Still in the club: not disabled.
        coVerify(exactly = 0) { playerRepository.updateActiveStatus(any(), any()) }
        // Out of the match that hasn't started; the played one keeps its call-up (history).
        coVerify { matchRepository.manageCallUps(upcoming.id, listOf(teammate.id)) }
        coVerify(exactly = 0) { matchRepository.manageCallUps(played.id, any()) }
    }

    @Test
    fun staffCannotRemoveAPlayer() = runBlocking<Unit> {
        assertFailsWith<ForbiddenException> { remove(user("staff", ClubRole.MEMBER, AgeGroupRole.STAFF), ag.id, player.id) }
        coVerify(exactly = 0) { playerRepository.removePlayerFromAgeGroup(any(), any()) }
    }

    @Test
    fun aPlayerNotInThePoolIsNotFound() = runBlocking<Unit> {
        coEvery { playerRepository.removePlayerFromAgeGroup(ag.id, "nope") } returns false
        assertFailsWith<NotFoundException> { remove(user("coord", ClubRole.COORDINATOR), ag.id, "nope") }
    }

    @Test
    fun disablingKeepsThePlayerInTheirAgeGroupsAndOnlyDropsUpcomingCallUps() = runBlocking {
        disable(user("coord", ClubRole.COORDINATOR), player.id)

        coVerify { playerRepository.updateActiveStatus(player.id, false) }
        // Still in every pool.
        coVerify(exactly = 0) { playerRepository.removePlayerFromAgeGroup(any(), any()) }
        // Out of the match that hasn't started; the played one keeps its call-up (history).
        coVerify { matchRepository.manageCallUps(upcoming.id, listOf(teammate.id)) }
        coVerify(exactly = 0) { matchRepository.manageCallUps(played.id, any()) }
    }

    @Test
    fun onlyCoordinatorsAndAdminsCanDisableEvenTheAgeGroupsCoachCant() = runBlocking<Unit> {
        assertFailsWith<ForbiddenException> { disable(user("coach", ClubRole.MEMBER, AgeGroupRole.COACH), player.id) }
        coVerify(exactly = 0) { playerRepository.updateActiveStatus(any(), any()) }
    }

    @Test
    fun aDisabledPlayerCantBeAddedBackToAPool() = runBlocking<Unit> {
        coEvery { playerRepository.getPlayersData(listOf(player.id)) } returns listOf(player.copy(active = false))
        coEvery { playerRepository.getPlayersByAgeGroup(ag.id) } returns emptyList()
        assertFailsWith<ValidationException> { add(user("coord", ClubRole.COORDINATOR), ag.id, AddPlayersToAgeGroupRequest(listOf(player.id))) }
    }

    @Test
    fun aCoordinatorReactivatesADisabledPlayer() = runBlocking {
        coEvery { playerRepository.getPlayerById(player.id) } returns player.copy(active = false)

        reactivate(user("coord", ClubRole.COORDINATOR), player.id)

        coVerify { playerRepository.updateActiveStatus(player.id, true) }
        coVerify(exactly = 0) { playerRepository.addPlayersToAgeGroup(any()) }
    }

    @Test
    fun onlyCoordinatorsAndAdminsCanReactivate() = runBlocking<Unit> {
        coEvery { playerRepository.getPlayerById(player.id) } returns player.copy(active = false)
        assertFailsWith<ForbiddenException> { reactivate(user("coach", ClubRole.MEMBER, AgeGroupRole.COACH), player.id) }
        coVerify(exactly = 0) { playerRepository.updateActiveStatus(any(), any()) }
    }

    @Test
    fun reactivatingAnActivePlayerChangesNothing() = runBlocking {
        reactivate(user("coord", ClubRole.COORDINATOR), player.id)
        coVerify(exactly = 0) { playerRepository.updateActiveStatus(any(), any()) }
    }

    @Test
    fun aReactivatedPlayerCanBeAddedToAPoolAgain() = runBlocking {
        coEvery { playerRepository.getPlayersData(listOf(player.id)) } returns listOf(player)
        coEvery { playerRepository.getPlayersByAgeGroup(ag.id) } returns emptyList()
        add(user("coord", ClubRole.COORDINATOR), ag.id, AddPlayersToAgeGroupRequest(listOf(player.id)))
        coVerify { playerRepository.addPlayersToAgeGroup(match { links -> links.single().let { it.ageGroupId == ag.id && it.playerId == player.id } }) }
    }

    @Test
    fun whenTheAdminSetsAdminsOnlyACoordinatorCanNeitherDisableNorReactivate() = runBlocking<Unit> {
        coEvery { clubPermissionRepository.getOverride(clubId, ConfigurablePermission.MANAGE_PLAYER_STATUS) } returns
            ClubPermissionSetting(ConfigurablePermission.MANAGE_PLAYER_STATUS, null, ClubRole.CLUB_ADMIN)
        val coordinator = user("coord", ClubRole.COORDINATOR)

        assertFailsWith<ForbiddenException> { disable(coordinator, player.id) }
        coEvery { playerRepository.getPlayerById(player.id) } returns player.copy(active = false)
        assertFailsWith<ForbiddenException> { reactivate(coordinator, player.id) }
        coVerify(exactly = 0) { playerRepository.updateActiveStatus(any(), any()) }

        // The admin still can.
        reactivate(user("admin", ClubRole.CLUB_ADMIN), player.id)
        coVerify { playerRepository.updateActiveStatus(player.id, true) }
    }
}
