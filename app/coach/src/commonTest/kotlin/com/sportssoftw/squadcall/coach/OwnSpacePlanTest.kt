package com.sportssoftw.squadcall.coach

import com.sportssoftw.squadcall.coach.domain.CoachAds
import com.sportssoftw.squadcall.coach.domain.GetSpacesUseCase
import com.sportssoftw.squadcall.coach.domain.LoadPlansUseCase
import com.sportssoftw.squadcall.coach.domain.OwnSpacePlan
import com.sportssoftw.squadcall.coach.domain.RequestPlanUseCase
import com.sportssoftw.squadcall.coach.domain.SpaceRepository
import com.sportssoftw.squadcall.model.Club
import com.sportssoftw.squadcall.domain.config.OwnSpace
import com.sportssoftw.squadcall.model.ClubStatus
import com.sportssoftw.squadcall.model.CoachSpace
import com.sportssoftw.squadcall.model.PlanDetails
import com.sportssoftw.squadcall.model.PlanFeature
import com.sportssoftw.squadcall.model.PlanFeatureLine
import com.sportssoftw.squadcall.model.PlansResponse
import com.sportssoftw.squadcall.model.SpaceAgeGroups
import com.sportssoftw.squadcall.model.SpacePlan
import com.sportssoftw.squadcall.model.SportType
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
        override suspend fun getSpaceDetails(spaceId: String): com.sportssoftw.squadcall.model.SpaceDetails = error("not used")
        override suspend fun checkAgeGroupRoom(spaceId: String) = error("not used")
        override suspend fun addAgeGroups(request: com.sportssoftw.squadcall.model.AddAgeGroupsRequest): com.sportssoftw.squadcall.model.AddedAgeGroups = error("not used")
        override suspend fun uploadAgeGroupCrest(ageGroupId: String, bytes: ByteArray): String = error("not used")
        override suspend fun imageBytes(path: String): ByteArray = error("not used")
        override suspend fun getPlans() = plans ?: error("offline")
        var requestFails = false
        // A local server sets the plan; a deployed one records the request.
        var setAtOnce = false
        var spacesLoaded = 0
        override suspend fun verifyGooglePurchase(productId: String, purchaseToken: String): PlansResponse = error("not used")
        override suspend fun requestPlan(plan: SpacePlan): PlansResponse {
            if (requestFails) error("offline")
            val now = plans!!.let { if (setAtOnce) it.copy(currentPlan = plan, requestedPlan = null) else it.copy(requestedPlan = plan) }
            plans = now
            return now
        }
        override suspend fun acceptInvite(code: String): CoachSpace = error("not used")
        override suspend fun deleteSpace(spaceId: String, currentPassword: String): Unit = error("not used")
        override suspend fun leaveSpace(spaceId: String, currentPassword: String): Unit = error("not used")
    }

    // Plans off (PLANS_ENABLED): the spaces load, the plans aren't fetched.
    @Test
    fun withPlansOffThePlansAreNotLoaded() = runTest {
        val ownPlan = OwnSpacePlan()
        val repository = Spaces(listOf(SpaceAgeGroups(space("mine"), isOwner = true, ageGroups = emptyList(), plan = SpacePlan("PRO"), ageGroupsThisSeason = 1, ageGroupsLeft = 0)))
        val getSpaces = GetSpacesUseCase(repository, ownPlan, LoadPlansUseCase(repository, ownPlan), CoachAds(), plansEnabled = { false })

        getSpaces()
        assertNull(ownPlan.plans.value)
        assertEquals("mine", ownPlan.space.value?.spaceId)
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
        val getSpaces = GetSpacesUseCase(repository, ownPlan, LoadPlansUseCase(repository, ownPlan), CoachAds())
        assertNull(ownPlan.space.value)

        // The own space as the server counts it, and the server's plans.
        getSpaces()
        assertEquals(OwnSpace("mine", SpacePlan("BASIC"), ageGroupsThisSeason = 2, ageGroupsLeft = 1, canAddAgeGroups = true), ownPlan.space.value)
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
    fun eachLoadKeepsWhichSpacesShowAdsAsTheServerSays() = runTest {
        val ownPlan = OwnSpacePlan()
        val ads = CoachAds()
        val repository = Spaces(
            listOf(
                SpaceAgeGroups(space("anas"), isOwner = false, ageGroups = emptyList(), plan = SpacePlan("PRO"), showsAds = false),
                SpaceAgeGroups(space("mine"), isOwner = true, ageGroups = emptyList(), plan = SpacePlan("FREE"), showsAds = true)
            )
        )
        val getSpaces = GetSpacesUseCase(repository, ownPlan, LoadPlansUseCase(repository, ownPlan), ads)

        getSpaces()
        assertEquals(setOf("mine"), ads.spacesWithAds.value)
        // Home stands for the coach's own space.
        assertEquals("mine", ads.homeSpaceId.value)

        // The plan goes up: no more ads, from the server's answer, not a rule in the app.
        repository.spaces = repository.spaces.map { it.copy(showsAds = false) }
        getSpaces()
        assertEquals(emptySet(), ads.spacesWithAds.value)

        // Staff only: Home stands for the first space they're in.
        repository.spaces = listOf(SpaceAgeGroups(space("anas"), isOwner = false, ageGroups = emptyList(), plan = SpacePlan("FREE"), showsAds = true))
        getSpaces()
        assertEquals("anas", ads.homeSpaceId.value)
        assertEquals(setOf("anas"), ads.spacesWithAds.value)
    }

    @Test
    fun aPlanAskedForShowsAsTheServerAnswers() = runTest {
        val ownPlan = OwnSpacePlan()
        val repository = Spaces(emptyList())
        val loadPlans = LoadPlansUseCase(repository, ownPlan)
        loadPlans()
        val requestPlan = RequestPlanUseCase(repository, ownPlan, GetSpacesUseCase(repository, ownPlan, loadPlans, CoachAds()))

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
