package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A non-admin is scoped to the season(s) of the age group(s) they're attached to (an `AgeGroupStaff`
 * role or a linked player in that age group's pool). Empty when they have none.
 */
class GetSeasonsUseCaseTest {

    private val seasonRepository = mockk<SeasonRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val userRepository = mockk<UserRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetSeasonsUseCase(
        seasonRepository, ageGroupRepository, userRepository, playerRepository, checkUserPermissionUseCase, transactionManager
    )

    private val clubIdA = "club-a"
    private val season2627 = "season-26-27"
    private val season2728 = "season-27-28"

    private fun ageGroup(id: String, seasonId: String) = AgeGroup(
        id = id, clubId = clubIdA, name = id, seasonId = seasonId, startYear = 2014, endYear = 2014
    )

    private val ag1 = ageGroup("ag-1", season2627)
    private val ag2 = ageGroup("ag-2", season2627)
    private val ag3 = ageGroup("ag-3", season2728)

    private fun season(id: String) = Season(
        id = id, clubId = clubIdA, name = id, type = SeasonType.EUROPEAN_YEAR,
        startDate = LocalDate(2026, 8, 1), endDate = LocalDate(2027, 6, 30), active = true
    )

    private fun user(id: String) = User(
        id = id, email = "$id@test.com", firstName = "Test", lastName = "User",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )

    private fun child(id: String) = Player(
        id = id, clubId = clubIdA, firstName = "Test", lastName = "Child",
        birthDate = LocalDate(2012, 1, 1), active = true
    )

    @Test
    fun clubAdminSeesEverySeasonInTheClub() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        val everySeason = listOf(season(season2627), season(season2728))
        coEvery { seasonRepository.getSeasonsByClub(clubIdA) } returns everySeason

        assertEquals(everySeason, useCase(admin.id, clubIdA))
        Unit
    }

    @Test
    fun plainMemberWithNoAgeGroupSeesNoSeasons() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { playerRepository.getPlayersByUser(member.id) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ag1, ag2, ag3)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(member.id, any()) } returns null

        assertEquals(emptyList(), useCase(member.id, clubIdA))
        Unit
    }

    @Test
    fun coachWithTwoAgeGroupsInTheSameSeasonSeesThatSeasonOnce() = runBlocking {
        val coach = user("p7-multi-ag-coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ag1, ag2, ag3)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1.id) } returns AgeGroupRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag2.id) } returns AgeGroupRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag3.id) } returns null
        coEvery { seasonRepository.getSeasonById(season2627) } returns season(season2627)

        assertEquals(listOf(season(season2627)), useCase(coach.id, clubIdA))
        Unit
    }

    @Test
    fun coachWithAgeGroupsInDifferentSeasonsSeesBothSeasons() = runBlocking {
        val coach = user("p8-cross-season-coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { playerRepository.getPlayersByUser(coach.id) } returns emptyList()
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ag1, ag3)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1.id) } returns AgeGroupRole.COACH
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag3.id) } returns AgeGroupRole.COACH
        coEvery { seasonRepository.getSeasonById(season2627) } returns season(season2627)
        coEvery { seasonRepository.getSeasonById(season2728) } returns season(season2728)

        assertEquals(setOf(season2627, season2728), useCase(coach.id, clubIdA).map { it.id }.toSet())
        Unit
    }

    @Test
    fun parentSeesOnlyTheirChildsAgeGroupSeason() = runBlocking {
        val parent = user("p10-parent")
        val kid = child("child-1")
        coEvery { userRepository.findById(parent.id) } returns parent
        coEvery { userRepository.getUserRoleInClub(parent.id, clubIdA) } returns null
        coEvery { playerRepository.getPlayersByUser(parent.id) } returns listOf(kid)
        coEvery { playerRepository.getAgeGroupPlayersByClub(clubIdA) } returns listOf(
            AgeGroupPlayer(id = "agp-1", ageGroupId = ag3.id, playerId = kid.id)
        )
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubIdA) } returns listOf(ag1, ag3)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(parent.id, any()) } returns null
        coEvery { seasonRepository.getSeasonById(season2728) } returns season(season2728)

        assertEquals(listOf(season(season2728)), useCase(parent.id, clubIdA))
        Unit
    }
}
