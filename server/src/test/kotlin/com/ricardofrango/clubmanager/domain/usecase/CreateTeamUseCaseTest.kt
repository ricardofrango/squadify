package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.TeamRepository
import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ForbiddenException
import com.ricardofrango.clubmanager.exception.ValidationException
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.CreateTeamRequest
import com.ricardofrango.clubmanager.model.Team
import com.ricardofrango.clubmanager.model.User
import com.ricardofrango.clubmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

/**
 * Covers the "Age groups & seasons" team-creation rows of `PERMISSION_TEST_MATRIX.md`.
 * CreateTeamUseCase already treats CLUB_ADMIN and COORDINATOR equivalently (it passes
 * `minClubRole = ClubRole.COORDINATOR`), so that part is correct. What's missing: it never checks
 * that `ageGroupId` actually belongs to `seasonId` (or to `clubId` at all) before creating the team.
 * See "issues to fix.md" -- "A team's season isn't validated against its age group's season."
 */
class CreateTeamUseCaseTest {

    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager)
    private val useCase = CreateTeamUseCase(teamRepository, ageGroupRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"

    private val consistentAgeGroup = AgeGroup(
        id = "age-group-1",
        clubId = clubIdA,
        name = "U12",
        seasonId = "season-1",
        birthYear = 2014
    )

    private val dummyTeam = Team(
        id = "generated-team-id",
        clubId = clubIdA,
        ageGroupId = "age-group-1",
        seasonId = "season-1",
        name = "Team 1",
        code = null,
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
    fun coordinatorCanCreateATeamJustLikeAnAdmin() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById("age-group-1") } returns consistentAgeGroup
        coEvery { teamRepository.createTeam(any()) } returns dummyTeam
        val request = CreateTeamRequest(clubId = clubIdA, ageGroupId = "age-group-1", seasonId = "season-1", name = "Team 1")

        useCase(coordinator.id, request)
        Unit
    }

    @Test
    fun plainMemberCannotCreateATeam() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        val request = CreateTeamRequest(clubId = clubIdA, ageGroupId = "age-group-1", seasonId = "season-1", name = "Team 1")

        assertFailsWith<ForbiddenException> {
            useCase(member.id, request)
        }
        Unit
    }

    @Test
    fun ageGroupFromADifferentSeasonIsRejected() = runBlocking {
        // Fixed: CreateTeamUseCase now checks that the age group actually belongs to the given club and
        // season before creating the team. See "issues to fix.md".
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { ageGroupRepository.getAgeGroupById("age-group-from-season-X") } returns AgeGroup(
            id = "age-group-from-season-X",
            clubId = clubIdA,
            name = "U14",
            seasonId = "season-X-actual", // deliberately inconsistent with the request's seasonId below
            birthYear = 2012
        )
        val request = CreateTeamRequest(
            clubId = clubIdA,
            ageGroupId = "age-group-from-season-X",
            seasonId = "season-Y", // deliberately inconsistent with the age group's real season
            name = "Team 1"
        )

        assertFailsWith<ValidationException> {
            useCase(admin.id, request)
        }
        Unit
    }
}
