package com.ricardofrango.squadify.coach.access

import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.access.allowed
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.User
import com.ricardofrango.squadify.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// The coach's rules (TECHNICAL_DECISIONS §72): the owner may do everything in every age group of the
// space, staff everything in their own age groups, and only the owner creates age groups and manages
// staff.
class CoachAccessPolicyTest {
    private val users = mockk<UserRepository>()
    private val ageGroups = mockk<AgeGroupRepository>()
    private val teams = mockk<TeamRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
    private val policy = CoachAccessPolicy(users, ageGroups, teams, transactionManager)

    private val space = "space-1"
    private val sub12 = ageGroup("sub-12", space)
    private val sub14 = ageGroup("sub-14", space)
    private val elsewhere = ageGroup("other-space-ag", "space-2")

    init {
        listOf("owner", "staff", "outsider").forEach { coEvery { users.findById(it) } returns user(it) }
        coEvery { users.findById("suspended") } returns user("suspended").copy(status = UserStatus.SUSPENDED)
        coEvery { users.getUserRoleInClub(any(), any()) } returns null
        coEvery { users.getUserRoleInClub("owner", space) } returns ClubRole.CLUB_ADMIN
        coEvery { users.getUserRoleInClub("staff", space) } returns ClubRole.MEMBER
        coEvery { users.getUserRoleInClub("suspended", space) } returns ClubRole.CLUB_ADMIN
        listOf(sub12, sub14, elsewhere).forEach { coEvery { ageGroups.getAgeGroupById(it.id) } returns it }
        coEvery { ageGroups.getAgeGroupsByClub(space) } returns listOf(sub12, sub14)
        coEvery { ageGroups.getUserRoleInAgeGroup(any(), any()) } returns null
        // Staff on Sub-14 only, and (wrongly) on another space's age group.
        coEvery { ageGroups.getUserRoleInAgeGroup("staff", sub14.id) } returns AgeGroupRole.COACH
        coEvery { ageGroups.getUserRoleInAgeGroup("staff", elsewhere.id) } returns AgeGroupRole.COACH
        coEvery { teams.getTeamById("sub-12-a") } returns Team("sub-12-a", space, sub12.id, "s1", "Sub-12 A", active = true)
        coEvery { teams.getTeamById("sub-14-a") } returns Team("sub-14-a", space, sub14.id, "s1", "Sub-14 A", active = true)
    }

    @Test
    fun theOwnerMayDoEverythingInEveryAgeGroupWithoutStaffRows() = runBlocking {
        val scope = GameScope(space, ageGroupId = sub12.id)
        assertTrue(allowed { policy.requireManageLiveMatch("owner", scope) })
        assertTrue(allowed { policy.requireManagePool("owner", scope) })
        assertTrue(allowed { policy.requireSeeRatings("owner", GameScope(space, teamId = "sub-12-a")) })
        assertTrue(allowed { policy.requireCreateAgeGroups("owner", space) })
        assertTrue(allowed { policy.requireManageStaff("owner", scope) })
        assertTrue(allowed { policy.requireEditClub("owner", space) })
        assertNull(policy.visibleAgeGroupIds("owner", space))
    }

    @Test
    fun staffManageTheirOwnAgeGroupsOnly() = runBlocking {
        val theirs = GameScope(space, ageGroupId = sub14.id)
        val other = GameScope(space, ageGroupId = sub12.id)
        assertTrue(allowed { policy.requireEditAgeGroup("staff", theirs) })
        assertTrue(allowed { policy.requireManageTeams("staff", theirs) })
        assertTrue(allowed { policy.requireManageMatch("staff", GameScope(space, teamId = "sub-14-a")) })
        assertFalse(allowed { policy.requireViewGames("staff", other) })
        assertFalse(allowed { policy.requireManageMatch("staff", GameScope(space, teamId = "sub-12-a")) })
        assertEquals(setOf(sub14.id), policy.visibleAgeGroupIds("staff", space))
    }

    @Test
    fun onlyTheOwnerCreatesAgeGroupsManagesStaffAndChangesTheSpace() = runBlocking {
        assertFalse(allowed { policy.requireCreateAgeGroups("staff", space) })
        assertFalse(allowed { policy.requireEditClub("staff", space) })
        assertFalse(allowed { policy.requireManageStaff("staff", GameScope(space, ageGroupId = sub14.id)) })
    }

    @Test
    fun staffSeeAndAddWhatBelongsToTheWholeSpace() = runBlocking {
        val wholeSpace = GameScope(space)
        assertTrue(allowed { policy.requireViewOpponents("staff", wholeSpace) })
        assertTrue(allowed { policy.requireCreateOpponent("staff", wholeSpace) })
        assertTrue(allowed { policy.requireCreatePlayers("staff", wholeSpace) })
        assertTrue(allowed { policy.requireViewClub("staff", space) })
    }

    @Test
    fun outsidersAndInactiveAccountsAreRefused() = runBlocking {
        assertFalse(allowed { policy.requireMember("outsider", space) })
        assertFalse(allowed { policy.requireViewOpponents("outsider", GameScope(space)) })
        assertFalse(allowed { policy.requireMember("suspended", space) })
    }

    @Test
    fun aStaffRowOnAnotherSpacesAgeGroupGivesNothingHere() = runBlocking {
        assertFalse(allowed { policy.requireManagePool("staff", GameScope(space, ageGroupId = elsewhere.id)) })
    }

    @Test
    fun anUnknownTeamIsRefused() = runBlocking {
        coEvery { teams.getTeamById("gone") } returns null
        assertFalse(allowed { policy.requireManageMatch("staff", GameScope(space, teamId = "gone")) })
    }

    private fun ageGroup(id: String, clubId: String) =
        AgeGroup(id = id, clubId = clubId, name = id, seasonId = "s1", startYear = 2014, endYear = 2014)

    private fun user(id: String) = User(
        id = id, email = "$id@test.com", firstName = "Test", lastName = "User",
        status = UserStatus.ACTIVE, isPlatformAdmin = false, createdAt = Instant.fromEpochMilliseconds(0)
    )
}
