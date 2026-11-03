package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.exception.PlanLimitException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// What a plan locks (REQUIREMENTS §5c): in each season the age groups after the first ones created, and
// on an age group the staff after the first to join, the owner taking one of its places.
class PlansTest {
    private val repository = mockk<SpacePlanRepository>()
    private val plans = CoachPlans(repository)

    @Test
    fun theFirstAgeGroupsOfEachSeasonStay() = runBlocking {
        coEvery { repository.ageGroupsInCreationOrder("s") } returns
            listOf("a1" to "25/26", "b1" to "26/27", "a2" to "25/26", "b2" to "26/27", "a3" to "25/26", "a4" to "25/26")

        assertEquals(setOf("a2", "a3", "a4", "b2"), plans.lockedAgeGroups("s", PlanLimits.FREE))
        assertEquals(setOf("a4"), plans.lockedAgeGroups("s", PlanLimits.BASIC))
        assertTrue(plans.lockedAgeGroups("s", PlanLimits.PRO).isEmpty())
    }

    @Test
    fun theFirstStaffToJoinStayWithTheOwnerTakingAPlace() = runBlocking {
        coEvery { repository.staffInJoinOrder("ag") } returns listOf("u1", "u2", "u3")

        assertEquals(setOf("u1", "u2", "u3"), plans.lockedStaff("ag", PlanLimits.FREE))
        assertEquals(setOf("u3"), plans.lockedStaff("ag", PlanLimits.BASIC))
        assertTrue(plans.lockedStaff("ag", PlanLimits.PRO).isEmpty())
    }

    @Test
    fun aFullAgeGroupHasNoRoomForAnotherCode() = runBlocking {
        coEvery { repository.planOf("s") } returns PlanLimits.BASIC
        coEvery { repository.staffInJoinOrder("ag") } returns listOf("u1")
        plans.requireRoomForStaff("s", "ag")

        coEvery { repository.staffInJoinOrder("ag") } returns listOf("u1", "u2")
        assertFailsWith<PlanLimitException> { plans.requireRoomForStaff("s", "ag") }
        Unit
    }
}
