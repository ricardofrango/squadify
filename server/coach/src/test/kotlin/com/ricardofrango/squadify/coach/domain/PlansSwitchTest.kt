package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.GooglePurchaseRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

// PLANS_ENABLED (REQUIREMENTS §5c): while plans are off every space counts as Pro with no ads, and a plan
// can't be asked for or bought; the stored plans are kept, so turning them on falls back to them.
class PlansSwitchTest {
    private val repository = mockk<SpacePlanRepository>()
    private val transactionManager = object : TransactionManager {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    @Test
    fun whileOffEverySpaceIsPro() = runBlocking {
        coEvery { repository.planOf("s") } returns PlanLimits.FREE
        val plans = CoachPlans(repository, plansEnabled = false)

        assertEquals(PlanLimits.PRO, plans.planOf("s"))
        assertFalse(PlanLimits.showsAds(plans.planOf("s")))
        assertEquals(emptySet(), plans.lockedAgeGroups("s", plans.planOf("s")))
    }

    @Test
    fun whenOnTheStoredPlanCounts() = runBlocking {
        coEvery { repository.planOf("s") } returns PlanLimits.BASIC
        val plans = CoachPlans(repository, plansEnabled = true)

        assertEquals(PlanLimits.BASIC, plans.planOf("s"))
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
