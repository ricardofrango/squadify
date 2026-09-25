package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchEventRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class GetAgeGroupDetailsUseCaseTest {

    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val matchRepository = mockk<MatchRepository>()
    private val matchEventRepository = mockk<MatchEventRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetAgeGroupDetailsUseCase(
        ageGroupRepository, clubRepository, seasonRepository, playerRepository, teamRepository,
        matchRepository, matchEventRepository, opponentRepository, checkUserPermissionUseCase, transactionManager
    )

    private val clubIdA = "club-a"
    private val ageGroupId = "ag-1"
    private val ageGroup = AgeGroup(id = ageGroupId, clubId = clubIdA, name = "Sub 2015", seasonId = "s1", startYear = 2015, endYear = 2015)
    private val season = Season(
        id = "s1", clubId = clubIdA, name = "26/27", type = SeasonType.EUROPEAN_YEAR,
        startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2027, 6, 30), active = true
    )
    private val teamInAg = Team(id = "t1", clubId = clubIdA, ageGroupId = ageGroupId, seasonId = "s1", name = "Sub 2015 A", code = null, active = true)
    private val teamOtherAg = Team(id = "t2", clubId = clubIdA, ageGroupId = "ag-other", seasonId = "s1", name = "Sub 2016 A", code = null, active = true)

    private fun user(id: String) = User(
        id = id, email = "$id@test.com", firstName = "Test", lastName = "User",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun stubReads() {
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns ageGroup
        coEvery { clubRepository.getClubById(clubIdA) } returns mockk(relaxed = true)
        coEvery { seasonRepository.getSeasonById("s1") } returns season
        coEvery { teamRepository.getTeamsByClub(clubIdA) } returns listOf(teamInAg, teamOtherAg)
        coEvery { matchRepository.getMatchesByTeam("t1") } returns emptyList()
        coEvery { playerRepository.getPlayersByAgeGroup(ageGroupId) } returns listOf(
            Player(id = "p1", clubId = clubIdA, firstName = "A", lastName = "B", birthDate = LocalDate(2016, 1, 1), active = true)
        )
        coEvery { ageGroupRepository.getAgeGroupStaff(ageGroupId) } returns emptyList()
        coEvery { opponentRepository.getOpponentsByClub(clubIdA) } returns emptyList()
    }

    @Test
    fun coordinatorGetsTheAggregateWithCanManageTrueAndOnlyThisAgeGroupsTeams() = runBlocking {
        val coord = user("p3-coordinator")
        coEvery { userRepository.findById(coord.id) } returns coord
        coEvery { userRepository.getUserRoleInClub(coord.id, clubIdA) } returns ClubRole.COORDINATOR
        stubReads()

        val result = useCase(coord.id, ageGroupId)

        assertEquals(listOf("t1"), result.teams.map { it.id })
        assertEquals("26/27", result.season.name)
        assertEquals(true, result.canManage)
        Unit
    }

    @Test
    fun ageGroupStaffCanReadButCanManageIsFalse() = runBlocking {
        // STAFF passes the read gate (minTeamRole = STAFF) but not the canManage probe (COACH).
        val staff = user("p6-staff")
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ageGroupId) } returns TeamRole.STAFF
        stubReads()

        val result = useCase(staff.id, ageGroupId)

        assertEquals(false, result.canManage)
        Unit
    }

    @Test
    fun aPlainMemberWithNoAgeGroupRoleOrLinkIsDenied() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { ageGroupRepository.getAgeGroupById(ageGroupId) } returns ageGroup
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, ageGroupId) } returns null
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()

        assertFailsWith<ForbiddenException> { useCase(member.id, ageGroupId) }
        Unit
    }

    @Test
    fun aMissingAgeGroupIs404() = runBlocking {
        coEvery { ageGroupRepository.getAgeGroupById("nope") } returns null
        assertFailsWith<NotFoundException> { useCase("anyone", "nope") }
        Unit
    }
}
