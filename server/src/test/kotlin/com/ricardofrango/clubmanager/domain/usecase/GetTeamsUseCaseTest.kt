package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.model.AgeGroupPlayer
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.Player
import com.ricardofrango.clubmanager.model.Team
import com.ricardofrango.clubmanager.model.TeamRole
import com.ricardofrango.clubmanager.model.User
import com.ricardofrango.clubmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * "List Club A's teams" rows of `PERMISSION_TEST_MATRIX.md`. Non-admins are scoped to teams whose
 * **age group** they're attached to -- an `AgeGroupStaff` role or a linked player in that age group's
 * pool.
 */
class GetTeamsUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager
    )
    private val useCase = GetTeamsUseCase(
        teamRepository, userRepository, playerRepository, ageGroupRepository, checkUserPermissionUseCase, transactionManager
    )

    private val clubIdA = "club-a"
    private val ag1 = "age-group-1"
    private val ag2 = "age-group-2"
    private val ag3 = "age-group-3"

    private fun team(id: String, ageGroupId: String) = Team(
        id = id, clubId = clubIdA, ageGroupId = ageGroupId, seasonId = "season-1",
        name = id, code = null, active = true
    )

    private val team1 = team("team-1", ag1)
    private val team2 = team("team-2", ag2)
    private val team3 = team("team-3", ag3)

    private fun user(id: String) = User(
        id = id, email = "$id@test.com", firstName = "Test", lastName = "User",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun child(id: String) = Player(
        id = id, clubId = clubIdA, firstName = "Test", lastName = "Child",
        birthDate = LocalDate(2012, 1, 1), active = true
    )

    @Test
    fun clubAdminSeesAllTeams() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)

        assertEquals(listOf(team1, team2, team3), useCase(admin.id, clubIdA))
        Unit
    }

    @Test
    fun plainMemberWithNoAgeGroupSeesNoTeams() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, any()) } returns null

        assertEquals(emptyList(), useCase(member.id, clubIdA))
        Unit
    }

    @Test
    fun coachOnlySeesTeamsInTheirAgeGroup() = runBlocking {
        val coach = user("p5-coach-ag1")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag2) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag3) } returns null

        assertEquals(listOf(team1), useCase(coach.id, clubIdA))
        Unit
    }

    @Test
    fun parentSeesEveryTeamInTheirChildsAgeGroup() = runBlocking {
        // The child is in ag2's pool, so the parent sees team2 (and would see any sibling team in ag2).
        val parent = user("p10-parent")
        val kid = child("child-1")
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(team1, team2, team3)
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(kid)
        coEvery { playerRepository.getAgeGroupPlayersByClub(clubIdA) } returns listOf(
            AgeGroupPlayer(id = "agp-1", ageGroupId = ag2, playerId = kid.id)
        )
        coEvery { playerRepository.getPlayersByAgeGroup(ag2) } returns listOf(kid)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, any()) } returns null

        assertEquals(listOf(team2), useCase(parent.id, clubIdA))
        Unit
    }
}
