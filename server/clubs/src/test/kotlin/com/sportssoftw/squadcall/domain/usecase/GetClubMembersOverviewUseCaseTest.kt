package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubMemberAccount
import com.sportssoftw.squadcall.domain.repository.ClubPermissionRepository
import com.sportssoftw.squadcall.domain.repository.MemberListFilter
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.InviteRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.EmailService
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.AgeGroup
import com.sportssoftw.squadcall.model.AgeGroupPlayer
import com.sportssoftw.squadcall.model.AgeGroupStaffMember
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.Invite
import com.sportssoftw.squadcall.model.InviteType
import com.sportssoftw.squadcall.model.Player
import com.sportssoftw.squadcall.model.Season
import com.sportssoftw.squadcall.model.SeasonType
import com.sportssoftw.squadcall.model.AgeGroupRole
import com.sportssoftw.squadcall.model.User
import com.sportssoftw.squadcall.model.UserPlayerRelationship
import com.sportssoftw.squadcall.model.UserStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Club settings → Members (design 34): members with what they do in the club, and pending invites. */
class GetClubMembersOverviewUseCaseTest {

    private val clubRepository = mockk<ClubRepository>()
    private val userRepository = mockk<UserRepository>()
    private val ageGroupRepository = mockk<AgeGroupRepository>()
    private val seasonRepository = mockk<SeasonRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val inviteRepository = mockk<InviteRepository>(relaxed = true)
    private val teamRepository = mockk<TeamRepository>()
    private val emailService = mockk<EmailService>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val clubPermissionRepository = mockk<ClubPermissionRepository>(relaxed = true)
    init { coEvery { clubPermissionRepository.getOverride(any(), any()) } returns null }
    private val check = CheckUserPermissionUseCase(userRepository, teamRepository, playerRepository, ageGroupRepository, transactionManager, clubPermissionRepository)
    private val useCase = GetClubMembersOverviewUseCase(
        clubRepository, userRepository, ageGroupRepository, seasonRepository, playerRepository, inviteRepository, check, transactionManager
    )
    private val invites = ManageClubInviteUseCase(inviteRepository, clubRepository, emailService, check, transactionManager)

    private val clubId = "club-1"
    private fun user(id: String, first: String) = User(id, "$id@t.com", "912", first, "L", status = UserStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0))

    private fun account(id: String, first: String, role: ClubRole) =
        ClubMemberAccount(id, first, "L", "$id@t.com", "912", role, Instant.fromEpochMilliseconds(0), null)

    private fun stub(viewerRole: ClubRole) {
        val viewer = user("viewer", "Vera")
        coEvery { userRepository.findById("viewer") } returns viewer
        coEvery { userRepository.getUserRoleInClub("viewer", clubId) } returns viewerRole
        coEvery { clubRepository.getClubMembersPage(clubId, any(), any(), any()) } returns listOf(
            account("viewer", "Vera", viewerRole),
            account("coach", "Joao", ClubRole.MEMBER)
        )
        coEvery { seasonRepository.getSeasonsByClub(clubId) } returns listOf(
            Season("s-now", clubId, "2026/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), active = true),
            Season("s-old", clubId, "2025/26", SeasonType.EUROPEAN_YEAR, LocalDate(2025, 9, 1), LocalDate(2026, 6, 30), active = false)
        )
        coEvery { ageGroupRepository.getAgeGroupsByClub(clubId) } returns listOf(
            AgeGroup("ag-old", clubId, "Sub-13", "s-old", 2013, 2013),
            AgeGroup("ag-now", clubId, "Sub-15", "s-now", 2011, 2011)
        )
        coEvery { ageGroupRepository.getAgeGroupStaff("ag-old") } returns listOf(AgeGroupStaffMember("coach", "Joao", "L", AgeGroupRole.STAFF))
        coEvery { ageGroupRepository.getAgeGroupStaff("ag-now") } returns listOf(AgeGroupStaffMember("coach", "Joao", "L", AgeGroupRole.COACH))
        val tomas = Player("p1", clubId, "Tomás", "Bragança", LocalDate(2011, 3, 12), shirtNumber = 10, active = true)
        coEvery { playerRepository.getAgeGroupPlayersByClub(clubId) } returns listOf(AgeGroupPlayer("x", "ag-now", "p1"))
        coEvery { playerRepository.getUserPlayerLinksByClub(clubId) } returns listOf(Triple("coach", tomas, UserPlayerRelationship.FATHER))
        coEvery { inviteRepository.findClubMemberInvites(clubId) } returns listOf(
            Invite("inv-1", "new@t.com", clubId, InviteType.CLUB_MEMBER, ClubRole.COORDINATOR, createdAt = Instant.fromEpochMilliseconds(5))
        )
    }

    @Test
    fun membersCarryTheirAgeGroupRolesCurrentSeasonFirstAndTheirLinkedPlayers() = runBlocking {
        stub(ClubRole.CLUB_ADMIN)
        val overview = useCase("viewer", clubId)

        val coach = overview.members.single { it.userId == "coach" }
        assertEquals(listOf("Sub-15" to AgeGroupRole.COACH, "Sub-13" to AgeGroupRole.STAFF), coach.ageGroupRoles.map { it.ageGroupName to it.role })
        assertEquals("Tomás Bragança", coach.linkedPlayers.single().name)
        assertEquals("Sub-15", coach.linkedPlayers.single().ageGroupName)
        assertEquals("912", coach.phone)
        assertEquals(listOf("new@t.com" to ClubRole.COORDINATOR), overview.pendingInvites.map { it.email to it.role })
        assertEquals(ClubRole.CLUB_ADMIN, overview.viewerRole)
        assertEquals(1, overview.adminCount)
    }

    @Test
    fun aPlainMemberCannotSeeTheMemberList() = runBlocking {
        stub(ClubRole.MEMBER)
        coEvery { playerRepository.getPlayersByUser("viewer") } returns emptyList()
        assertFailsWith<ForbiddenException> { useCase("viewer", clubId) }
        Unit
    }

    @Test
    fun invitesCanBeResentAndCancelledOnlyWithinTheirClub() = runBlocking {
        stub(ClubRole.COORDINATOR)
        coEvery { clubRepository.getClubById(clubId) } returns null
        coEvery { inviteRepository.getById("inv-1") } returns
            Invite("inv-1", "new@t.com", clubId, InviteType.CLUB_MEMBER, ClubRole.MEMBER, createdAt = Instant.fromEpochMilliseconds(5))
        coEvery { inviteRepository.getById("inv-other") } returns
            Invite("inv-other", "x@t.com", "club-2", InviteType.CLUB_MEMBER, ClubRole.MEMBER, createdAt = Instant.fromEpochMilliseconds(5))

        invites.resend("viewer", clubId, "inv-1")
        coVerify { emailService.sendClubInvite(to = "new@t.com", clubName = any()) }
        invites.cancel("viewer", clubId, "inv-1")
        coVerify { inviteRepository.deleteInvite("inv-1") }
        assertFailsWith<NotFoundException> { invites.cancel("viewer", clubId, "inv-other") }
        Unit
    }

    @Test
    fun aPageSearchesOnTheServerAndCountsRolesForTheWholeClub() = runBlocking {
        stub(ClubRole.CLUB_ADMIN)
        // Two asked for, three came back: the third only says another page follows.
        coEvery { clubRepository.getClubMembersPage(clubId, MemberListFilter(ClubRole.MEMBER, "joao", null), 3, 2) } returns listOf(
            account("coach", "João", ClubRole.MEMBER), account("m2", "João", ClubRole.MEMBER), account("m3", "João", ClubRole.MEMBER)
        )
        coEvery { ageGroupRepository.getAgeGroupStaff(any()) } returns emptyList()
        coEvery { clubRepository.countClubMembersByRole(clubId, null) } returns mapOf(ClubRole.CLUB_ADMIN to 2, ClubRole.MEMBER to 40)
        coEvery { clubRepository.countClubMembersByRole(clubId, "joao") } returns mapOf(ClubRole.MEMBER to 5)

        // Case and accents don't matter: "  JOÃO " is searched as "joao".
        val page = useCase.page("viewer", clubId, ClubRole.MEMBER, "  JOÃO ", memberUserId = null, limit = 2, offset = 2)

        assertEquals(listOf("coach", "m2"), page.members.map { it.userId })
        assertTrue(page.hasMore)
        assertEquals(42, page.memberCount)
        assertEquals(2, page.adminCount)
        assertEquals(5, page.matchingCountOf(ClubRole.MEMBER))
        assertEquals(0, page.matchingCountOf(ClubRole.CLUB_ADMIN))
        assertFalse(page.pendingInvites.isEmpty(), "pending invites always come whole")
    }
}
