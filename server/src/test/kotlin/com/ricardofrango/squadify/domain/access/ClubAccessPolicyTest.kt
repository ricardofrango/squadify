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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The clubs server's policy makes exactly the permission checks the game use cases made before it
// existed (TECHNICAL_DECISIONS §72): same roles, same configurable permission, same context.
class ClubAccessPolicyTest {
    private val check = mockk<CheckUserPermissionUseCase>()
    private val policy = ClubAccessPolicy(check)
    private val scope = GameScope("c1", teamId = "t1")

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
    fun allowedTurnsARefusalIntoNo() = runBlocking {
        coEvery { check("u2", any(), any(), any(), any(), any(), any(), any(), any()) } throws ForbiddenException("no")
        assertTrue(allowed { policy.requireMember("u1", "c1") })
        assertFalse(allowed { policy.requireMember("u2", "c1") })
    }
}
