package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Match
import com.ricardofrango.footballmanager.model.MatchStatus
import com.ricardofrango.footballmanager.model.Player
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
import kotlin.test.assertFailsWith

/**
 * Covers GetMatchesUseCase's no-`teamId` path per "issues to fix.md" -- "A plain club member sees the
 * whole club, not just 'the club.'" and `PERMISSION_TEST_MATRIX.md`'s "List Club A's matches (no team
 * filter)" row: a coach with only Team 1 should see Team 1's matches, not every team's.
 */
class GetMatchesUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val userRepository = mockk<UserRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, transactionManager)
    private val useCase = GetMatchesUseCase(matchRepository, teamRepository, userRepository, playerRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"

    private fun team(id: String) = Team(
        id = id,
        clubId = clubIdA,
        ageGroupId = "age-group-1",
        seasonId = "season-1",
        name = id,
        code = null,
        active = true
    )

    private val team1 = team("team-1")
    private val team2 = team("team-2")

    private fun user(id: String) = User(
        id = id,
        email = "$id@test.com",
        firstName = "Test",
        lastName = "User",
        status = UserStatus.ACTIVE,
        isPlatformAdmin = false,
        createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun match(id: String, teamId: String) = Match(
        id = id,
        clubId = clubIdA,
        teamId = teamId,
        opponentId = "opponent-1",
        location = null,
        dateTime = Instant.fromEpochMilliseconds(0),
        status = MatchStatus.SCHEDULED,
        notes = null
    )

    @Test
    fun clubAdminSeesEveryMatchInTheClubWithNoTeamFilter() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        val everyMatch = listOf(match("m1", team1.id), match("m2", team2.id))
        coEvery { matchRepository.getMatchesByClub(clubIdA) } returns everyMatch

        val result = useCase(admin.id, clubIdA)

        assertEquals(everyMatch, result)
        Unit
    }

    @Test
    fun coachWithNoTeamFilterOnlySeesTheirOwnTeamsMatches() = runBlocking {
        // Fixed: previously this hit the same unscoped `teamId == null` branch as every other list use
        // case and returned every team's matches. Now scoped to the caller's own TeamRole assignments.
        val coach = user("p5-coach-team1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2)
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team1.id) } returns TeamRole.COACH
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team2.id) } returns null
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        val team1Matches = listOf(match("m1", team1.id))
        coEvery { matchRepository.getMatchesByTeam(team1.id) } returns team1Matches

        val result = useCase(coach.id, clubIdA)

        assertEquals(team1Matches, result)
        Unit
    }

    @Test
    fun plainMemberWithNoTeamAssignmentSeesNoMatchesWithNoTeamFilter() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2)
        coEvery { teamRepository.getUserRoleInTeam(member.id, team1.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(member.id, team2.id) } returns null
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        val result = useCase(member.id, clubIdA)

        assertEquals(emptyList(), result)
        Unit
    }

    @Test
    fun parentWithNoTeamFilterOnlySeesTheirChildsTeamsMatches() = runBlocking {
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
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2)
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team1.id) } returns null
        coEvery { teamRepository.getUserRoleInTeam(parent.id, team2.id) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(child)
        coEvery { playerRepository.getTeamPlayersByClub(clubIdA) } returns listOf(
            TeamPlayer(id = "tp-1", teamId = team2.id, playerId = child.id, startDate = LocalDate(2020, 1, 1), status = TeamPlayerStatus.ACTIVE)
        )
        val team2Matches = listOf(match("m2", team2.id))
        coEvery { matchRepository.getMatchesByTeam(team2.id) } returns team2Matches

        val result = useCase(parent.id, clubIdA)

        assertEquals(team2Matches, result)
        Unit
    }

    @Test
    fun coachOfTheRequestedTeamCanSeeItsMatchesWhenFilteredByTeamId() = runBlocking {
        val coach = user("p5-coach-team1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getUserRoleInTeam(coach.id, team1.id) } returns TeamRole.COACH
        val matches = listOf(match("m1", team1.id))
        coEvery { matchRepository.getMatchesByTeam(team1.id) } returns matches

        val result = useCase(coach.id, clubIdA, team1.id)

        assertEquals(matches, result)
        Unit
    }

    @Test
    fun outsiderIsDeniedWhenFilteredByTeamId() = runBlocking {
        val outsider = user("p12-outsider")
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubIdA) } returns null
        coEvery { teamRepository.getUserRoleInTeam(outsider.id, team1.id) } returns null
        coEvery { playerRepository.getPlayersByUser(outsider.id) } returns emptyList()

        assertFailsWith<ForbiddenException> {
            useCase(outsider.id, clubIdA, team1.id)
        }
        Unit
    }
}
