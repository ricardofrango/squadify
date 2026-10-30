package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class GetOpponentListUseCaseTest {

    private val opponentRepository = mockk<OpponentRepository>()
    private val clubRepository = mockk<ClubRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val userRepository = mockk<UserRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetOpponentListUseCase(
        opponentRepository, clubRepository, ageGroupRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager
    )

    private val clubId = "club-1"
    private val ag1 = "ag-1"
    private val club = Club(clubId, "CD Ribamar", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)

    private fun user(id: String) = User(id, "$id@t.com", firstName = "T", lastName = "U", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))

    init {
        coEvery { clubRepository.getClubById(clubId) } returns club
        coEvery { opponentRepository.getOpponentsByClub(clubId) } returns listOf(
            Opponent("o2", clubId, "GD Marinhas"),
            Opponent("o1", clubId, "académica Foz do Vez"),
            Opponent("o3", clubId, "AD Fontelas")
        )
        coEvery { opponentRepository.hasOpponents(clubId) } returns true
    }

    private fun coordinator(): String {
        val coordinator = user("coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubId) } returns ClubRole.COORDINATOR
        return coordinator.id
    }

    @Test
    fun aPageAsksForOneRowMoreToKnowIfThereIsAnother() = runBlocking<Unit> {
        val page = (1..3).map { Opponent("p$it", clubId, "Opponent $it") }
        coEvery { opponentRepository.getOpponentsPage(clubId, null, 3, 4) } returns page

        val result = useCase(coordinator(), clubId, limit = 2, offset = 4)

        assertEquals(listOf("p1", "p2"), result.opponents.map { it.id })
        assertTrue(result.hasMore)
        assertTrue(result.anyOpponents)
    }

    @Test
    fun theLastPageHasNoMore() = runBlocking<Unit> {
        coEvery { opponentRepository.getOpponentsPage(clubId, null, 3, 0) } returns listOf(Opponent("p1", clubId, "Opponent 1"))

        val result = useCase(coordinator(), clubId, limit = 2)

        assertEquals(1, result.opponents.size)
        assertFalse(result.hasMore)
    }

    @Test
    fun theSearchIsSentLowercaseWithoutAccents() = runBlocking<Unit> {
        coEvery { opponentRepository.getOpponentsPage(clubId, "academica", 31, 0) } returns emptyList()

        val result = useCase(coordinator(), clubId, search = "  Académica ", limit = 30)

        assertTrue(result.opponents.isEmpty())
        // Nothing matches, but the club has opponents: "nothing matches", not "no opponents yet".
        assertTrue(result.anyOpponents)
    }

    @Test
    fun aClubWithNoOpponentsHasNone() = runBlocking<Unit> {
        coEvery { opponentRepository.getOpponentsPage(clubId, null, 31, 0) } returns emptyList()
        coEvery { opponentRepository.hasOpponents(clubId) } returns false

        assertFalse(useCase(coordinator(), clubId, limit = 30).anyOpponents)
    }

    @Test
    fun aCoordinatorGetsTheListByNameAndCanCreate() = runBlocking<Unit> {
        val coordinator = user("coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubId) } returns ClubRole.COORDINATOR

        val result = useCase(coordinator.id, clubId)

        assertEquals("CD Ribamar", result.clubName)
        assertEquals(listOf("o1", "o3", "o2"), result.opponents.map { it.id })
        assertTrue(result.canCreate)
    }

    @Test
    fun aPlainMemberCanSeeTheListButNotCreate() = runBlocking<Unit> {
        val member = user("member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubId) } returns ClubRole.MEMBER

        val result = useCase(member.id, clubId)

        assertEquals(3, result.opponents.size)
        assertFalse(result.canCreate)
    }

    @Test
    fun aCoachCanCreateWhenPickingForTheirOwnAgeGroup() = runBlocking<Unit> {
        val coach = user("coach")
        coEvery { userRepository.findById(coach.id) } returns coach
        coEvery { userRepository.getUserRoleInClub(coach.id, clubId) } returns null
        coEvery { ageGroupRepository.getAgeGroupById(ag1) } returns AgeGroup(ag1, clubId, "Sub-15", "s-1", 2012, 2012)
        coEvery { ageGroupRepository.getUserRoleInAgeGroup(coach.id, ag1) } returns AgeGroupRole.COACH

        assertTrue(useCase(coach.id, clubId, ageGroupId = ag1).canCreate)
    }

    @Test
    fun someoneOutsideTheClubIsDenied() = runBlocking<Unit> {
        val outsider = user("outsider")
        coEvery { userRepository.findById(outsider.id) } returns outsider
        coEvery { userRepository.getUserRoleInClub(outsider.id, clubId) } returns null

        assertFailsWith<ForbiddenException> { useCase(outsider.id, clubId) }
    }
}
