package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.SpacePlanRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

// A plan asked for on a deployed server (REQUIREMENTS §5c): recorded, not set, and every platform admin
// emailed; the request stands when an email doesn't go. Locally it's set at once (CoachPlansTest).
class PlanRequestsTest {
    private val now = Instant.parse("2026-11-03T10:00:00Z")
    private val plans = mockk<SpacePlanRepository>(relaxed = true)
    private val mailer = mockk<PlanRequestMailer>()
    private val letter = slot<PlanRequestEmail>()

    private fun requestPlan(atOnce: Boolean): RequestPlanUseCase {
        val clubs = mockk<ClubRepository>()
        coEvery { clubs.getMemberships("owner") } returns listOf(
            mockk<Club> {
                every { id } returns "space1"
                every { name } returns "Ana's team"
            } to ClubRole.CLUB_ADMIN
        )
        val users = mockk<UserRepository>()
        coEvery { users.findById("owner") } returns mockk<User> {
            every { firstName } returns "Ana"
            every { lastName } returns "Silva"
            every { email } returns "ana@example.com"
        }
        coEvery { plans.planOf("space1") } returns PlanLimits.FREE
        val transactionManager = object : TransactionManager {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        val clock = object : Clock { override fun now() = now }
        return RequestPlanUseCase(clubs, users, plans, mailer, setOf("admin@example.com", "other@example.com"), atOnce, transactionManager, clock)
    }

    @Test
    fun deployedTheRequestIsRecordedAndTheAdminsEmailed() = runBlocking {
        coEvery { mailer.sendPlanRequest(any(), capture(letter)) } returns false
        coEvery { plans.requestedPlan("space1") } returns PlanLimits.PRO

        val response = requestPlan(atOnce = false)("owner", PlanLimits.PRO)

        coVerify { plans.requestPlan("space1", PlanLimits.PRO, "owner", now) }
        coVerify(exactly = 0) { plans.setPlan(any(), any(), any()) }
        coVerify { mailer.sendPlanRequest("admin@example.com", any()) }
        coVerify { mailer.sendPlanRequest("other@example.com", any()) }
        assertEquals(PlanRequestEmail("space1", "Ana's team", "Ana Silva", "ana@example.com", PlanLimits.FREE, PlanLimits.PRO), letter.captured)
        assertEquals(PlanLimits.FREE, response.currentPlan)
        assertEquals(PlanLimits.PRO, response.requestedPlan)
    }

    @Test
    fun locallyItsSetAtOnceAndNobodyIsEmailed() = runBlocking {
        requestPlan(atOnce = true)("owner", SpacePlan("BASIC"))

        coVerify { plans.setPlan("space1", PlanLimits.BASIC, now) }
        coVerify(exactly = 0) { plans.requestPlan(any(), any(), any(), any()) }
        coVerify(exactly = 0) { mailer.sendPlanRequest(any(), any()) }
    }
}
