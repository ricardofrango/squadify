package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerListFilter
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.slot
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A non-admin sees only the players of age groups they're attached to (an `AgeGroupStaff` role or a
 * linked player in that age group's pool). A `teamId` filter resolves to the team's age-group pool.
 */
class GetPlayersUseCaseTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val userRepository = mockk<UserRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetPlayersUseCase(
        playerRepository, teamRepository, ageGroupRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager
    )

    private val clubIdA = "club-a"
    private val ag1 = "age-group-1"
    private val ag2 = "age-group-2"

    private fun ageGroup(id: String) = AgeGroup(id = id, clubId = clubIdA, name = id, seasonId = "s1", startYear = 2014, endYear = 2014)
    private fun team(id: String, ageGroupId: String) = Team(
        id = id, clubId = clubIdA, ageGroupId = ageGroupId, seasonId = "s1", name = id, code = null, active = true
    )

    private val team1 = team("team-1", ag1)
    private val team2 = team("team-2", ag2)

    private fun user(id: String) = User(
        id = id, email = "$id@test.com", firstName = "Test", lastName = "User",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun player(id: String) = Player(
        id = id, clubId = clubIdA, firstName = "Test", lastName = id,
        birthDate = LocalDate(2012, 1, 1), active = true
    )

    @Test
    fun clubAdminSeesEveryPlayerInTheClub() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        val everyone = listOf(player("a"), player("b"))
        coEvery { playerRepository.getPlayersByClub(clubIdA) } returns everyone

        assertEquals(everyone, useCase(admin.id, clubIdA))
        Unit
    }

    @Test
    fun plainMemberWithNoAgeGroupSeesNoPlayers() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ageGroup(ag1), ageGroup(ag2))
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, any()) } returns null

        assertEquals(emptyList(), useCase(member.id, clubIdA))
        Unit
    }

    @Test
    fun coachWithNoTeamFilterOnlySeesTheirOwnAgeGroupsPlayers() = runBlocking {
        val coach = user("p5-coach-ag1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ageGroup(ag1), ageGroup(ag2))
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag2) } returns null
        val ag1Players = listOf(player("p1"), player("p2"))
        coEvery { playerRepository.getPlayersByAgeGroup(ag1) } returns ag1Players

        assertEquals(ag1Players, useCase(coach.id, clubIdA))
        Unit
    }

    @Test
    fun parentWithNoTeamFilterOnlySeesTheirChildsAgeGroupPlayers() = runBlocking {
        val parent = user("p10-parent")
        val kid = player("child-1")
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(kid)
        coEvery { playerRepository.getAgeGroupPlayersByClub(clubIdA) } returns listOf(
            AgeGroupPlayer(id = "agp-1", ageGroupId = ag2, playerId = kid.id)
        )
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ageGroup(ag1), ageGroup(ag2))
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, any()) } returns null
        val ag2Players = listOf(kid)
        coEvery { playerRepository.getPlayersByAgeGroup(ag2) } returns ag2Players

        assertEquals(ag2Players, useCase(parent.id, clubIdA))
        Unit
    }

    @Test
    fun coachOfTheRequestedTeamsAgeGroupSeesItsPoolWhenFilteredByTeamId() = runBlocking {
        val coach = user("p5-coach-ag1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamById(team1.id) } returns team1
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        val pool = listOf(player("p1"))
        coEvery { playerRepository.getPlayersByAgeGroup(ag1) } returns pool

        assertEquals(pool, useCase(coach.id, clubIdA, team1.id))
        Unit
    }

    @Test
    fun outsiderIsDeniedWhenFilteredByTeamId() = runBlocking {
        val outsider = user("p12-outsider")
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubIdA) } returns null
        coEvery { teamRepository.getTeamById(team1.id) } returns team1
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(outsider.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(outsider.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(outsider.id, clubIdA, team1.id)
        }
        Unit
    }

    @Test
    fun aNonexistentTeamIsNotFoundWhenFilteredByTeamId() = runBlocking {
        // A club admin passes the permission check on club role alone, regardless of whether the
        // team resolves -- so this reaches GetPlayersUseCase's own team lookup.
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { teamRepository.getTeamById("no-such-team") } returns null

        assertFailsWith<NotFoundException> {
            useCase(admin.id, clubIdA, "no-such-team")
        }
        Unit
    }

    // ---- Paged (the Players screen) ----

    @Test
    fun anAdminsPageFiltersBySeasonAgeGroupAndAFoldedSearch() = runBlocking {
        val admin = user("admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ageGroup(ag1), ageGroup(ag2).copy(seasonId = "s0"))
        val filter = slot<PlayerListFilter>()
        // Two asked for, three came back: the third only says another page follows.
        coEvery { playerRepository.getPlayersPage(clubIdA, capture(filter), 3, 0) } returns listOf(player("a"), player("b"), player("c"))

        val page = useCase.page(admin.id, clubIdA, seasonId = "s1", ageGroupId = ag1, search = " Tomás ", limit = 2, offset = 0)

        // Everyone in the club (no visibility limit), the season as its age groups, and "Tomás" as "tomas".
        assertEquals(PlayerListFilter(null, setOf(ag1), ag1, "tomas"), filter.captured)
        assertEquals(listOf("a", "b"), page.players.map { it.lastName })
        assertTrue(page.hasMore)
        assertTrue(page.anyPlayers)
        Unit
    }

    @Test
    fun aCoachsPageOnlyCoversTheirAgeGroupsPools() = runBlocking {
        val coach = user("coach-ag1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ageGroup(ag1), ageGroup(ag2))
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag2) } returns null
        coEvery { playerRepository.getPlayersPage(clubIdA, PlayerListFilter(visibleAgeGroupIds = setOf(ag1)), 31, 30) } returns emptyList()
        coEvery { playerRepository.hasPlayers(clubIdA, PlayerListFilter(visibleAgeGroupIds = setOf(ag1))) } returns false

        val page = useCase.page(coach.id, clubIdA, seasonId = null, ageGroupId = null, search = "  ", limit = 30, offset = 30)

        assertEquals(emptyList(), page.players)
        assertFalse(page.hasMore)
        assertFalse(page.anyPlayers, "nothing in their age groups at all")
        Unit
    }
}
