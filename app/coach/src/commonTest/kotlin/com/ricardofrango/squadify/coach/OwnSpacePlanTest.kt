package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.coach.domain.LoadPlansUseCase
import com.ricardofrango.squadify.coach.domain.OwnSpacePlan
import com.ricardofrango.squadify.coach.domain.RequestPlanUseCase
import com.ricardofrango.squadify.coach.domain.SpaceRepository
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.domain.config.OwnSpace
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.PlanDetails
import com.ricardofrango.squadify.model.PlanFeature
import com.ricardofrango.squadify.model.PlanFeatureLine
import com.ricardofrango.squadify.model.PlansResponse
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.time.Instant

// The own space behind the plan messages (REQUIREMENTS §5c): the coach's own space's plan and age groups
// this season, whatever plan the spaces they're staff in have, and the server's plans.
class OwnSpacePlanTest {
    private fun space(id: String) =
        Club(id = id, name = id, status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)

    private class Spaces(var spaces: List<SpaceAgeGroups>) : SpaceRepository {
        var plans: PlansResponse? = PlansResponse(
            listOf(PlanDetails(SpacePlan("FREE"), "Free", listOf(PlanFeatureLine(PlanFeature.LIVE_GAME, "Live game recording")), 1, 1)),
            currentPlan = SpacePlan("FREE")
        )
        override suspend fun getSpaces() = spaces.also { spacesLoaded++ }
        override suspend fun getPlans() = plans ?: error("offline")
        var requestFails = false
        // A local server sets the plan; a deployed one records the request.
        var setAtOnce = false
        var spacesLoaded = 0
        override suspend fun requestPlan(plan: SpacePlan): PlansResponse {
            if (requestFails) error("offline")
            val now = plans!!.let { if (setAtOnce) it.copy(currentPlan = plan, requestedPlan = null) else it.copy(requestedPlan = plan) }
            plans = now
            return now
        }
        override suspend fun setUpSpace(request: SetUpSpaceRequest): SpaceSetup = error("not used")
        override suspend fun acceptInvite(code: String): CoachSpace = error("not used")
    }

    @Test
    fun eachLoadKeepsTheOwnSpace() = runTest {
        val ownPlan = OwnSpacePlan()
        val repository = Spaces(
            listOf(
                SpaceAgeGroups(space("mine"), isOwner = true, ageGroups = emptyList(), plan = SpacePlan("BASIC"), ageGroupsThisSeason = 2, ageGroupsLeft = 1),
                SpaceAgeGroups(space("anas"), isOwner = false, ageGroups = emptyList(), plan = SpacePlan("FREE"))
            )
        )
        val getSpaces = GetSpacesUseCase(repository, ownPlan, LoadPlansUseCase(repository, ownPlan))
        assertNull(ownPlan.space.value)

        // The own space as the server counts it, and the server's plans.
        getSpaces()
        assertEquals(OwnSpace("mine", SpacePlan("BASIC"), ageGroupsThisSeason = 2, ageGroupsLeft = 1), ownPlan.space.value)
        assertEquals(listOf(SpacePlan("FREE")), ownPlan.plans.value?.plans?.map { it.plan })

        // The plans can't be fetched: the spaces still load, and the last plans stay.
        repository.plans = null
        getSpaces()
        assertEquals(listOf(SpacePlan("FREE")), ownPlan.plans.value?.plans?.map { it.plan })

        // Staff only: no own plan, so See plans opens Plans with none marked as theirs.
        repository.spaces = listOf(SpaceAgeGroups(space("anas"), isOwner = false, ageGroups = emptyList(), plan = SpacePlan("FREE")))
        getSpaces()
        assertNull(ownPlan.space.value)
    }

    @Test
    fun aPlanAskedForShowsAsTheServerAnswers() = runTest {
        val ownPlan = OwnSpacePlan()
        val repository = Spaces(emptyList())
        val loadPlans = LoadPlansUseCase(repository, ownPlan)
        loadPlans()
        val requestPlan = RequestPlanUseCase(repository, ownPlan, GetSpacesUseCase(repository, ownPlan, loadPlans))

        // Not sent: nothing changes.
        repository.requestFails = true
        assertFailsWith<IllegalStateException> { requestPlan(SpacePlan("PRO")) }
        assertNull(ownPlan.plans.value?.requestedPlan)

        // Deployed: asked for, the plan the same, the spaces not loaded again.
        repository.requestFails = false
        assertFalse(requestPlan(SpacePlan("PRO")))
        assertEquals(SpacePlan("PRO"), ownPlan.plans.value?.requestedPlan)
        assertEquals(SpacePlan("FREE"), ownPlan.plans.value?.currentPlan)
        assertEquals(0, repository.spacesLoaded)

        // Locally: set at once, and the spaces loaded again for the new plan's limits.
        repository.setAtOnce = true
        assertTrue(requestPlan(SpacePlan("BASIC")))
        assertEquals(SpacePlan("BASIC"), ownPlan.plans.value?.currentPlan)
        assertNull(ownPlan.plans.value?.requestedPlan)
        assertEquals(1, repository.spacesLoaded)
    }
}
