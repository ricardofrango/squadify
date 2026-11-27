package com.sportssoftw.squadcall.coach.domain

import com.sportssoftw.squadcall.coach.data.SpacePlanRepository
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.model.GooglePurchaseRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// PLANS_ENABLED (REQUIREMENTS §5c): while plans are off every space counts as Pro and shows ads, and a plan
// can't be asked for or bought; the stored plans are kept, so turning them on falls back to them.
class PlansSwitchTest {
    private val repository = mockk<SpacePlanRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    @Test
    fun whileOffEverySpaceIsProAndShowsAds() = runBlocking {
        coEvery { repository.planOf("s") } returns PlanLimits.FREE
        val plans = CoachPlans(repository, plansEnabled = false)

        assertEquals(PlanLimits.PRO, plans.planOf("s"))
        assertTrue(plans.showsAds(plans.planOf("s")))
        assertEquals(emptySet(), plans.lockedAgeGroups("s", plans.planOf("s")))
    }

    @Test
    fun whenOnTheStoredPlanCounts() = runBlocking {
        coEvery { repository.planOf("s") } returns PlanLimits.BASIC
        val plans = CoachPlans(repository, plansEnabled = true)

        assertEquals(PlanLimits.BASIC, plans.planOf("s"))
    }

    @Test
    fun whenOnOnlyFreeShowsAds() {
        val plans = CoachPlans(repository, plansEnabled = true)

        assertTrue(plans.showsAds(PlanLimits.FREE))
        assertFalse(plans.showsAds(PlanLimits.BASIC))
        assertFalse(plans.showsAds(PlanLimits.PRO))
    }

    @Test
    fun whileOffAPlanCannotBeAskedFor() = runBlocking {
        val request = RequestPlanUseCase(
            mockk<ClubRepository>(), mockk<UserRepository>(), repository, mockk<PlanRequestMailer>(),
            emptySet(), atOnce = true, transactionManager = transactionManager, plansEnabled = false
        )

        assertFailsWith<ForbiddenException> { request("owner", PlanLimits.PRO) }
        coVerify(exactly = 0) { repository.setPlan(any(), any(), any()) }
        coVerify(exactly = 0) { repository.requestPlan(any(), any(), any(), any()) }
    }

    @Test
    fun whileOffAStorePurchaseIsRefused() = runBlocking {
        val verify = VerifyGooglePurchaseUseCase(
            mockk<ClubRepository>(), mockk(), mockk(), mockk<GetPlansUseCase>(), transactionManager, plansEnabled = false
        )

        assertFailsWith<ForbiddenException> { verify("owner", GooglePurchaseRequest("coach_pro", "token")) }
        Unit
    }
}
