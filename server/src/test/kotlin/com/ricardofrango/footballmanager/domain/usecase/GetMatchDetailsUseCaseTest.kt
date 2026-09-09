package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.CallUpStatus
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Match
import com.ricardofrango.footballmanager.model.MatchCallUp
import com.ricardofrango.footballmanager.model.MatchCallUpWithPlayer
import com.ricardofrango.footballmanager.model.Opponent
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.Season
import com.ricardofrango.footballmanager.model.SeasonType
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class GetMatchDetailsUseCaseTest {

    private val matchRepository = mockk<MatchRepository>()
    private val opponentRepository = mockk<OpponentRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager
    )
    private val useCase = GetMatchDetailsUseCase(
        matchRepository, opponentRepository, teamRepository, ageGroupRepository, seasonRepository,
        playerRepository, checkUserPermissionUseCase, transactionManager
    )

    private val clubId = "club-1"
    private val matchId = "m-1"
    private val teamId = "t-1"
    private val ag1 = "ag-1"

    private val match = Match(matchId, clubId, teamId, "opp-1", "Field", Instant.fromEpochMilliseconds(1), notes = "n")
    private val team = Team(teamId, clubId, ag1, "s-1", "Sub-15 A", "A", true)
    private val ageGroup = AgeGroup(ag1, clubId, "Sub-15", "s-1", 2011)
    private val season = Season("s-1", clubId, "26/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 7, 1), LocalDate(2027, 6, 30), true)
    private val opponent = Opponent("opp-1", clubId, "AD Fontelas", "AD Fontelas", null)

    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
    private fun player(id: String) = Player(id, clubId, id, "P", LocalDate(2012, 1, 1), null, null, true)

    private fun baseStubs() {
        coEvery { matchRepository.getMatchById(matchId) } returns match
        coEvery { opponentRepository.getOpponentById("opp-1") } returns opponent
        coEvery { teamRepository.getTeamById(teamId) } returns team
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns ageGroup
        coEvery { seasonRepository.getSeasonById("s-1") } returns season
        coEvery { matchRepository.getCallUpsForMatch(matchId) } returns listOf(
            MatchCallUpWithPlayer(MatchCallUp("cu-1", matchId, "p-called", CallUpStatus.CONFIRMED), player("p-called"))
        )
        coEvery { playerRepository.getPlayersByAgeGroup(ag1) } returns listOf(player("p-called"), player("p-free"))
    }

    @Test
    fun coachSeesDetailsWithPoolMinusCalledUpAndCanManage() = runBlocking {
        val coach = user("coach")
        baseStubs()
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns TeamRole.COACH

        val d = useCase(coach.id, matchId)

        assertEquals("Sub-15", d.ageGroup.name)
        assertEquals("26/27", d.season.name)
        assertEquals(listOf("p-called"), d.callUps.map { it.player.id })
        // Pool is the age group minus whoever is already called up.
        assertEquals(listOf("p-free"), d.poolPlayers.map { it.id })
        assertTrue(d.canManage)
    }

    @Test
    fun ageGroupStaffCanReadButNotManage() = runBlocking {
        val staff = user("staff")
        baseStubs()
        coEvery { userRepository.findById(staff.id) } returns staff
        coEvery { userRepository.getUserRoleInClub(staff.id, clubId) } returns ClubRole.MEMBER
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(staff.id, ag1) } returns TeamRole.STAFF

        assertFalse(useCase(staff.id, matchId).canManage)
    }

    @Test
    fun outsiderIsDenied() = runBlocking {
        val outsider = user("outsider")
        baseStubs()
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubId) } returns null
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(outsider.id, ag1) } returns null
        coEvery { playerRepository.getPlayersByUser(outsider.id) } returns emptyList()

        assertFailsWith<ForbiddenException> { useCase(outsider.id, matchId) }
        Unit
    }
}
