package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.Club
import com.sportssoftw.squadcall.model.SportType
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.ClubStatus
import com.sportssoftw.squadcall.model.ClubTheme
import com.sportssoftw.squadcall.model.UpdateClubRequest
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Covers the "Update Club A profile" rows of `PERMISSION_TEST_MATRIX.md`. Admin and coordinator are
 * supposed to be fully equivalent until billing exists, but UpdateClubUseCase requires
 * `minClubRole = ClubRole.CLUB_ADMIN` specifically, excluding COORDINATOR. See "issues to fix.md".
 */
class UpdateClubUseCaseTest {

    private val clubRepository = mockk<ClubRepository>()
    private val userRepository = mockk<UserRepository>()
    private val teamRepository = mockk<TeamRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val checkUserPermissionUseCase = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository)
    private val useCase = UpdateClubUseCase(clubRepository, checkUserPermissionUseCase, transactionManager)

    private val clubIdA = "club-a"
    private val club = Club(
        id = clubIdA,
        name = "Club A",
        status = ClubStatus.ACTIVE,
        createdAt = Instant.fromEpochMilliseconds(0),
        sport = SportType.FOOTBALL
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
    fun clubAdminCanUpdateTheClubProfile() = runBlocking {
        val admin = user("p2-club-admin")
        coEvery { userRepository.findById(admin.id) } returns admin
        coEvery { userRepository.getUserRoleInClub(admin.id, clubIdA) } returns ClubRole.CLUB_ADMIN
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        coEvery { clubRepository.updateClub(any()) } returns true

        useCase(admin.id, clubIdA, UpdateClubRequest(name = "New Name"))
        Unit
    }

    @Test
    fun plainMemberCannotUpdateTheClubProfile() = runBlocking {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { clubRepository.getClubById(clubIdA) } returns club

        assertFailsWith<ForbiddenException> {
            useCase(member.id, clubIdA, UpdateClubRequest(name = "New Name"))
        }
        Unit
    }

    @Test
    fun coordinatorCanUpdateTheClubProfile() = runBlocking {
        // Fixed: the only planned difference between CLUB_ADMIN and COORDINATOR is billing access, not
        // implemented yet, so a coordinator can now do this too. UpdateClubUseCase now passes
        // minClubRole = COORDINATOR instead of CLUB_ADMIN. See "issues to fix.md".
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        coEvery { clubRepository.updateClub(any()) } returns true

        useCase(coordinator.id, clubIdA, UpdateClubRequest(name = "New Name"))
        Unit
    }

    @Test
    fun blankNameIsRejectedBeforeTouchingTheRepository() = runBlocking {
        assertFailsWith<ValidationException> {
            useCase("someone", clubIdA, UpdateClubRequest(name = "   "))
        }
        Unit
    }

    @Test
    fun coordinatorCanSetTheStadiumLocation() = runBlocking {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        val updated = slot<Club>()
        coEvery { clubRepository.updateClub(capture(updated)) } returns true

        useCase(coordinator.id, clubIdA, UpdateClubRequest(stadiumLocation = "Estrada da Praia, Ribamar"))

        assert(updated.captured.stadiumLocation == "Estrada da Praia, Ribamar")
    }

    private fun coordinatorSaving(existing: Club = club): Pair<String, io.mockk.CapturingSlot<Club>> {
        val coordinator = user("p3-coordinator")
        coEvery { userRepository.findById(coordinator.id) } returns coordinator
        coEvery { userRepository.getUserRoleInClub(coordinator.id, clubIdA) } returns ClubRole.COORDINATOR
        coEvery { clubRepository.getClubById(clubIdA) } returns existing
        val updated = slot<Club>()
        coEvery { clubRepository.updateClub(capture(updated)) } returns true
        return coordinator.id to updated
    }

    private val benfica = ClubTheme("#E30613", "#FFFFFF")

    @Test
    fun clubColoursAreSavedInCapitals() = runBlocking {
        val (userId, updated) = coordinatorSaving()
        useCase(userId, clubIdA, UpdateClubRequest(theme = ClubTheme("#e30613", "#ffffff")))
        assertEquals(benfica, updated.captured.theme)
    }

    @Test
    fun anUpdateWithoutColoursKeepsThem() = runBlocking {
        val (userId, updated) = coordinatorSaving(club.copy(theme = benfica))
        useCase(userId, clubIdA, UpdateClubRequest(name = "New Name"))
        assertEquals(benfica, updated.captured.theme)
    }

    @Test
    fun clearThemeRemovesBothColours() = runBlocking {
        val (userId, updated) = coordinatorSaving(club.copy(theme = benfica))
        useCase(userId, clubIdA, UpdateClubRequest(clearTheme = true))
        assertNull(updated.captured.theme)
    }

    @Test
    fun newColoursAndClearThemeTogetherAreRefused() = runBlocking<Unit> {
        val (userId, _) = coordinatorSaving()
        assertFailsWith<ValidationException> { useCase(userId, clubIdA, UpdateClubRequest(theme = benfica, clearTheme = true)) }
    }

    @Test
    fun aMainColourTooLightForWhiteTextIsRefused() = runBlocking<Unit> {
        val (userId, _) = coordinatorSaving()
        // Yellow main, white second: white text on yellow can't be read.
        assertFailsWith<ValidationException> { useCase(userId, clubIdA, UpdateClubRequest(theme = ClubTheme("#FFD700", "#FFFFFF"))) }
    }

    @Test
    fun aSecondColourThatIsntClearlyLighterIsRefused() = runBlocking<Unit> {
        val (userId, _) = coordinatorSaving()
        // Red and green are about as dark as each other.
        assertFailsWith<ValidationException> { useCase(userId, clubIdA, UpdateClubRequest(theme = ClubTheme("#D6001C", "#009A44"))) }
        // The right two colours the wrong way round.
        assertFailsWith<ValidationException> { useCase(userId, clubIdA, UpdateClubRequest(theme = ClubTheme("#FDE100", "#000000"))) }
    }

    @Test
    fun aColourThatIsntHexIsRefused() = runBlocking<Unit> {
        val (userId, _) = coordinatorSaving()
        assertFailsWith<ValidationException> { useCase(userId, clubIdA, UpdateClubRequest(theme = ClubTheme("red", "#FFFFFF"))) }
        assertFailsWith<ValidationException> { useCase(userId, clubIdA, UpdateClubRequest(theme = ClubTheme("#E30613", "#FFF"))) }
    }

    @Test
    fun aPlainMemberCantChangeTheColours() = runBlocking<Unit> {
        val member = user("p4-plain-member")
        coEvery { userRepository.findById(member.id) } returns member
        coEvery { userRepository.getUserRoleInClub(member.id, clubIdA) } returns ClubRole.MEMBER
        coEvery { clubRepository.getClubById(clubIdA) } returns club
        assertFailsWith<ForbiddenException> { useCase(member.id, clubIdA, UpdateClubRequest(theme = benfica)) }
    }
}
