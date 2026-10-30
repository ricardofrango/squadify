package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.ClubAccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerListFilter
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Minimal first coverage for this previously-untested use case -- see "Not found" is modelled
 * inconsistently for the same resource" in PROCESS_FLOWS.md §11: the age group lookup now throws
 * NotFoundException (was ValidationException) to match every other Age Group lookup in the codebase.
 */
class GetEligiblePlayersForAgeGroupUseCaseTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(
        userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository
    )
    private val useCase = GetEligiblePlayersForAgeGroupUseCase(
        playerRepository, ageGroupRepository, ClubAccessPolicy(checkUserPermissionUseCase), transactionManager
    )

    @Test
    fun aNonexistentAgeGroupIsNotFound() = runBlocking {
        // The age group lookup happens before the permission check, so no user stubs are needed here.
        coEvery { ageGroupRepository.getAgeGroupById("no-such-age-group") } returns null

        assertFailsWith<NotFoundException> {
            useCase("anyone", "no-such-age-group")
        }
        Unit
    }

    private val clubId = "club-1"
    private val ageGroup = AgeGroup("ag-1", clubId, "Sub-15", "s-1", 2011, 2012)
    // Who the page asks for: not in the pool yet, active, born in the age group's younger year or later.
    private val eligible = PlayerListFilter(notInAgeGroupId = "ag-1", bornFromYear = 2012, activeOnly = true)

    private fun coordinator(): String {
        val user = User("coordinator", "c@t.com", firstName = "C", lastName = "O", status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0))
        coEvery { userRepository.findById(user.id) } returns user
        coEvery { userRepository.getUserRoleInClub(user.id, clubId) } returns ClubRole.COORDINATOR
        coEvery { ageGroupRepository.getAgeGroupById("ag-1") } returns ageGroup
        return user.id
    }

    private fun player(id: String) = Player(id, clubId, "First $id", "Last", LocalDate(2012, 3, 1), active = true)

    @Test
    fun aPageAsksForTheEligibleOnesWithOneRowMore() = runBlocking<Unit> {
        coEvery { playerRepository.getPlayersPage(clubId, eligible, 3, 2) } returns listOf(player("p1"), player("p2"), player("p3"))

        val page = useCase.page(coordinator(), "ag-1", search = null, limit = 2, offset = 2)

        assertEquals(listOf("p1", "p2"), page.players.map { it.id })
        assertTrue(page.hasMore)
        assertTrue(page.anyPlayers)
    }

    @Test
    fun theSearchIsFoldedAndAnEmptyResultStillKnowsThereAreEligiblePlayers() = runBlocking<Unit> {
        coEvery { playerRepository.getPlayersPage(clubId, eligible.copy(search = "joao"), 31, 0) } returns emptyList()
        coEvery { playerRepository.hasPlayers(clubId, eligible) } returns true

        val page = useCase.page(coordinator(), "ag-1", search = " João ", limit = 30, offset = 0)

        assertTrue(page.players.isEmpty())
        assertFalse(page.hasMore)
        assertTrue(page.anyPlayers)
    }

    @Test
    fun noEligiblePlayerAtAll() = runBlocking<Unit> {
        coEvery { playerRepository.getPlayersPage(clubId, eligible, 31, 0) } returns emptyList()
        coEvery { playerRepository.hasPlayers(clubId, eligible) } returns false

        assertFalse(useCase.page(coordinator(), "ag-1", search = null, limit = 30, offset = 0).anyPlayers)
    }
}
