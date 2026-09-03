package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Season
import com.ricardofrango.footballmanager.model.SeasonType
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamPlayer
import com.ricardofrango.footballmanager.model.TeamPlayerStatus
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Covers GetSeasonsUseCase per "issues to fix.md" -- "A plain club member sees the whole club, not just
 * 'the club.'" and `PERMISSION_TEST_MATRIX.md`'s "List Club A's seasons/age groups" row: rather than a
 * hard deny for a caller with no team assignment, the list is scoped down to the season(s) their own
 * team(s) actually belong to (empty when they have none) -- the same filtering approach as
 * GetTeamsUseCase, applied consistently across every list use case, including players/parents.
 */
class GetSeasonsUseCaseTest {

    private val seasonRepository = mockk<SeasonRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val userRepository = mockk<UserRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, transactionManager)
    private val useCase = GetSeasonsUseCase(seasonRepository, teamRepository, userRepository, playerRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"
    private val season2627 = "season-26-27"
    private val season2728 = "season-27-28"

    private fun team(id: String, seasonId: String) = Team(
        id = id,
        clubId = clubIdA,
        ageGroupId = "age-group-$id",
        seasonId = seasonId,
        name = id,
        code = null,
        active = true
    )

    private val team1 = team("team-1", season2627)
    private val team2 = team("team-2", season2627)
    private val team3 = team("team-3", season2728)

    private fun season(id: String) = Season(
        id = id,
        clubId = clubIdA,
        name = id,
        type = SeasonType.EUROPEAN_YEAR,
        startDate = LocalDate(2026, 8, 1),
        endDate = LocalDate(2027, 6, 30),
        active = true
    )

    private fun user(id: String) = User(
        id = id,
        email = "$id@test.com",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        isPlatformAdmin = false,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    @Test
    fun clubAdminSeesEverySeasonInTheClub() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        val everySeason = listOf(season(season2627), season(season2728))
        coEvery { seasonRepository.getSeasonsByClub(clubIdA) } returns everySeason

        val result = useCase(admin.id, clubIdA)

        assertEquals(everySeason, result)
        Unit
    }

    @Test
    fun plainMemberWithNoTeamAssignmentSeesNoSeasons() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)
        coEvery { teamRepository.getUserRoleInTeam(member.id, team1.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(member.id, team2.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(member.id, team3.id) } returns null
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        val result = useCase(member.id, clubIdA)

        assertEquals(emptyList(), result)
        Unit
    }

    @Test
    fun coachWithTwoTeamsInTheSameSeasonSeesThatSeasonOnce() = runBlocking {
        // A coach on Team 1 and Team 2 (both season 26/27) sees season 26/27 exactly once, not twice.
        val coach = user("p7-multi-team-coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team1.id) } returns TeamRole.COACH
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team2.id) } returns TeamRole.COACH
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team3.id) } returns null
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        coEvery { seasonRepository.getSeasonById(season2627) } returns season(season2627)

        val result = useCase(coach.id, clubIdA)

        assertEquals(listOf(season(season2627)), result)
        Unit
    }

    @Test
    fun coachWithTeamsInDifferentSeasonsSeesBothSeasons() = runBlocking {
        val coach = user("p8-cross-season-coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team3)
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team1.id) } returns TeamRole.COACH
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team3.id) } returns TeamRole.COACH
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        coEvery { seasonRepository.getSeasonById(season2627) } returns season(season2627)
        coEvery { seasonRepository.getSeasonById(season2728) } returns season(season2728)

        val result = useCase(coach.id, clubIdA)

        assertEquals(setOf(season2627, season2728), result.map { it.id }.toSet())
        Unit
    }

    @Test
    fun parentSeesOnlyTheirChildsTeamsSeason() = runBlocking {
        val parent = user("p10-parent")
        val child = Player(
            id = "child-1",
            clubId = clubIdA,
            firstName = "Test",
            lastName = "Child",
            birthDate = LocalDate(2012, 1, 1),
            active = true
        )
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team3)
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team1.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team3.id) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)
        coEvery { playerRepository.getTeamPlayersByClub(clubIdA) } returns listOf(
            TeamPlayer(id = "tp-1", teamId = team3.id, playerId = child.id, startDate = LocalDate(2020, 1, 1), status = TeamPlayerStatus.ACTIVE)
        )
        coEvery { seasonRepository.getSeasonById(season2728) } returns season(season2728)

        val result = useCase(parent.id, clubIdA)

        assertEquals(listOf(season(season2728)), result)
        Unit
    }
}
