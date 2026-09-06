package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.AgeGroupPlayer
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Match
import com.ricardofrango.footballmanager.model.MatchStatus
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Team
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
 * A non-admin sees only matches of teams in age groups they're attached to (an `AgeGroupStaff` role
 * or a linked player in that age group's pool). A `teamId` filter resolves to its age group for the
 * permission check.
 */
class GetMatchesUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val userRepository = mockk<UserRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager
    )
    private val useCase = GetMatchesUseCase(
        matchRepository, teamRepository, userRepository, playerRepository, ageGroupRepository,
        checkUserPermissionUseCase, transactionManager
    )

    private val clubIdA = "club-a"
    private val ag1 = "age-group-1"
    private val ag2 = "age-group-2"

    private fun ageGroup(id: String) = AgeGroup(id = id, clubId = clubIdA, name = id, seasonId = "s1", birthYear = 2014)
    private fun team(id: String, ageGroupId: String) = Team(
        id = id, clubId = clubIdA, ageGroupId = ageGroupId, seasonId = "s1", name = id, code = null, active = true
    )

    private val team1 = team("team-1", ag1)
    private val team2 = team("team-2", ag2)

    private fun user(id: String) = User(
        id = id, email = "$id@test.com", firstName = "Test", lastName = "User",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun match(id: String, teamId: String) = Match(
        id = id, clubId = clubIdA, teamId = teamId, opponentId = "opponent-1", location = null,
        dateTime = Instant.fromEpochMilliseconds(0), status = MatchStatus.SCHEDULED, notes = null
    )

    private fun child(id: String) = Player(
        id = id, clubId = clubIdA, firstName = "Test", lastName = "Child",
        birthDate = LocalDate(2012, 1, 1), active = true
    )

    @Test
    fun clubAdminSeesEveryMatchInTheClubWithNoTeamFilter() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        val everyMatch = listOf(match("m1", team1.id), match("m2", team2.id))
        coEvery { matchRepository.getMatchesByClub(clubIdA) } returns everyMatch

        assertEquals(everyMatch, useCase(admin.id, clubIdA))
        Unit
    }

    @Test
    fun coachWithNoTeamFilterOnlySeesTheirOwnAgeGroupsMatches() = runBlocking {
        val coach = user("p5-coach-ag1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ageGroup(ag1), ageGroup(ag2))
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag2) } returns null
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2)
        val team1Matches = listOf(match("m1", team1.id))
        coEvery { matchRepository.getMatchesByTeam(team1.id) } returns team1Matches

        assertEquals(team1Matches, useCase(coach.id, clubIdA))
        Unit
    }

    @Test
    fun plainMemberWithNoAgeGroupSeesNoMatchesWithNoTeamFilter() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ageGroup(ag1), ageGroup(ag2))
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, any()) } returns null
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2)

        assertEquals(emptyList(), useCase(member.id, clubIdA))
        Unit
    }

    @Test
    fun parentWithNoTeamFilterOnlySeesTheirChildsAgeGroupMatches() = runBlocking {
        val parent = user("p10-parent")
        val kid = child("child-1")
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(kid)
        coEvery { playerRepository.getAgeGroupPlayersByClub(clubIdA) } returns listOf(
            AgeGroupPlayer(id = "agp-1", ageGroupId = ag2, playerId = kid.id)
        )
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ageGroup(ag1), ageGroup(ag2))
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, any()) } returns null
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2)
        val team2Matches = listOf(match("m2", team2.id))
        coEvery { matchRepository.getMatchesByTeam(team2.id) } returns team2Matches

        assertEquals(team2Matches, useCase(parent.id, clubIdA))
        Unit
    }

    @Test
    fun coachOfTheRequestedTeamsAgeGroupCanSeeItsMatchesWhenFilteredByTeamId() = runBlocking {
        val coach = user("p5-coach-ag1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamById(team1.id) } returns team1
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
        val matches = listOf(match("m1", team1.id))
        coEvery { matchRepository.getMatchesByTeam(team1.id) } returns matches

        assertEquals(matches, useCase(coach.id, clubIdA, team1.id))
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
}
