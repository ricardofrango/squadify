package com.ricardofrango.squadify.domain.access

import com.ricardofrango.squadify.domain.usecase.CheckUserPermissionUseCase
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The clubs server's policy makes exactly the permission checks the game and structure use cases made
// before it existed (TECHNICAL_DECISIONS §72): same roles, same configurable permission, same context.
class ClubAccessPolicyTest {
    private val check = mockk<CheckUserPermissionUseCase>()
    private val policy = ClubAccessPolicy(check)
    private val scope = GameScope("c1", teamId = "t1")
    private val ageGroup = GameScope("c1", ageGroupId = "ag1")

    init {
        coEvery { check(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
    }

    @Test
    fun memberNeedsOnlyTheClub() = runBlocking {
        policy.requireMember("u1", "c1")
        coVerify { check("u1", "c1", ClubRole.MEMBER, null, null, null, null, false, any()) }
    }

    @Test
    fun viewingGamesLetsPlayersAndParentsRead() = runBlocking {
        policy.requireViewGames("u1", GameScope("c1", ageGroupId = "ag1"))
        coVerify { check("u1", "c1", ClubRole.MEMBER, AgeGroupRole.STAFF, null, null, "ag1", true, any()) }
    }

    @Test
    fun eachManageCheckIsItsConfigurablePermission() = runBlocking {
        policy.requireManageMatch("u1", scope, "m")
        policy.requireManageCallUps("u1", scope, "c")
        policy.requireManageLiveMatch("u1", scope, "l")
        coVerify { check("u1", "c1", ClubRole.COORDINATOR, null, ConfigurablePermission.MANAGE_MATCH, "t1", null, false, "m") }
        coVerify { check("u1", "c1", ClubRole.COORDINATOR, null, ConfigurablePermission.MANAGE_CALL_UPS, "t1", null, false, "c") }
        coVerify { check("u1", "c1", ClubRole.COORDINATOR, null, ConfigurablePermission.MANAGE_LIVE_MATCH, "t1", null, false, "l") }
    }

    @Test
    fun ratingsAreForCoordinatorsAndTheAgeGroupsStaff() = runBlocking {
        policy.requireSeeRatings("u1", scope)
        coVerify { check("u1", "c1", ClubRole.COORDINATOR, AgeGroupRole.STAFF, null, "t1", null, false, AccessPolicy.DEFAULT_MESSAGE) }
    }

    @Test
    fun theClubsListsLetPlayersAndParentsRead() = runBlocking {
        policy.requireViewClub("u1", "c1")
        coVerify { check("u1", "c1", ClubRole.MEMBER, null, null, null, null, true, any()) }
    }

    @Test
    fun visibleAgeGroupsComeFromTheClubCheck() = runBlocking {
        coEvery { check.visibleAgeGroupIds("u1", "c1") } returns setOf("ag1")
        assertEquals(setOf("ag1"), policy.visibleAgeGroupIds("u1", "c1"))
    }

    @Test
    fun staffOnlyChecksLeavePlayersAndParentsOut() = runBlocking {
        policy.requireStaff("u1", ageGroup)
        policy.requireViewOpponents("u1", ageGroup)
        coVerify { check("u1", "c1", ClubRole.MEMBER, AgeGroupRole.STAFF, null, null, "ag1", false, any()) }
        coVerify { check("u1", "c1", ClubRole.MEMBER, AgeGroupRole.COACH, null, null, "ag1", false, any()) }
    }

    @Test
    fun fixedStructureChecksAreForCoordinators() = runBlocking {
        policy.requireViewMembers("u1", "c1")
        policy.requireCreateAgeGroups("u1", "c1", "a")
        policy.requireEditAgeGroup("u1", ageGroup, "e")
        policy.requireManageStaff("u1", ageGroup, "s")
        policy.requireEditClub("u1", "c1", "l")
        coVerify { check("u1", "c1", ClubRole.COORDINATOR, null, null, null, null, false, AccessPolicy.DEFAULT_MESSAGE) }
        coVerify { check("u1", "c1", ClubRole.COORDINATOR, null, null, null, null, false, "a") }
        coVerify { check("u1", "c1", ClubRole.COORDINATOR, null, null, null, null, false, "e") }
        coVerify { check("u1", "c1", ClubRole.COORDINATOR, null, null, null, null, false, "s") }
        coVerify { check("u1", "c1", ClubRole.COORDINATOR, null, null, null, null, false, "l") }
    }

    @Test
    fun editingATeamIsForCoordinatorsAndItsCoaches() = runBlocking {
        policy.requireEditTeam("u1", scope, "t")
        coVerify { check("u1", "c1", ClubRole.COORDINATOR, AgeGroupRole.COACH, null, "t1", null, false, "t") }
    }

    @Test
    fun eachStructureManageCheckIsItsConfigurablePermission() = runBlocking {
        policy.requireManageTeams("u1", ageGroup, "1")
        policy.requireCreatePlayers("u1", ageGroup, "2")
        policy.requireEditPlayer("u1", ageGroup, "3")
        policy.requireManagePool("u1", ageGroup, "4")
        policy.requireCreateOpponent("u1", ageGroup, "5")
        policy.requireEditOpponent("u1", ageGroup, "6")
        listOf(
            ConfigurablePermission.CREATE_TEAM to "1",
            ConfigurablePermission.CREATE_PLAYERS to "2",
            ConfigurablePermission.EDIT_PLAYER to "3",
            ConfigurablePermission.ASSIGN_PLAYERS_TO_AGE_GROUP to "4",
            ConfigurablePermission.CREATE_OPPONENT to "5",
            ConfigurablePermission.EDIT_OPPONENT to "6"
        ).forEach { (permission, message) ->
            coVerify { check("u1", "c1", ClubRole.COORDINATOR, null, permission, null, "ag1", false, message) }
        }
    }

    @Test
    fun allowedTurnsARefusalIntoNo() = runBlocking {
        coEvery { check("u2", any(), any(), any(), any(), any(), any(), any(), any()) } throws ForbiddenException("no")
        assertTrue(allowed { policy.requireMember("u1", "c1") })
        assertFalse(allowed { policy.requireMember("u2", "c1") })
    }
}
