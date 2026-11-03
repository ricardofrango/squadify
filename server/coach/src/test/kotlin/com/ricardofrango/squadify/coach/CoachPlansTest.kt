package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.domain.PlanLimits
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AdminSpace
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import com.ricardofrango.squadify.model.CreateMatchEventRequest
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.ManageCallUpRequest
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.GooglePurchaseRequest
import com.ricardofrango.squadify.model.PlanChange
import com.ricardofrango.squadify.model.PlansResponse
import com.ricardofrango.squadify.model.PlanFeature
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.RequestPlanRequest
import com.ricardofrango.squadify.model.SetSpacePlanRequest
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.StaffInvite
import com.ricardofrango.squadify.model.StartMatchRequest
import com.ricardofrango.squadify.model.TeamDetails
import com.ricardofrango.squadify.model.UpdateMatchStatusRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

// The plans on the coach server end to end (REQUIREMENTS §5c): a new space is on Free (one age group,
// the owner alone, no stats), a platform admin raises it, and going back down locks what's over the
// limits, the first age groups created and the first staff to join staying, until the plan allows them.
class CoachPlansTest {

    private suspend fun HttpResponse.isPlanLimit() =
        status == HttpStatusCode.Forbidden && body<ErrorResponse>().code == "PLAN_001"

    private suspend fun HttpClient.ageGroup(token: String, spaceId: String, name: String, year: Int) =
        post("/v1/age-groups") { json(token, CreateAgeGroupsRequest(spaceId, items = listOf(AgeGroupItemRequest(name, year, year)))) }

    @Test
    fun freeIsOneAgeGroupTheOwnerAloneAndNoStats() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val setup = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015), "Sub-12 A"))
        }.body<SpaceSetup>()
        val spaceId = setup.space.id
        assertEquals(PlanLimits.FREE, api.get("/v1/spaces") { bearerAuth(owner) }.body<List<SpaceAgeGroups>>().single().plan)

        assertTrue(api.ageGroup(owner, spaceId, "Sub-14", 2013).isPlanLimit())
        assertTrue(api.post("/v1/age-groups/${setup.ageGroup.id}/invites") { bearerAuth(owner) }.isPlanLimit())

        // The live game and history stay; stats don't.
        val player = api.post("/v1/players") {
            json(owner, CreatePlayersRequest(spaceId, listOf(CreatePlayer("Rui", "Costa", LocalDate(2015, 1, 1))), ageGroupId = setup.ageGroup.id))
        }.body<List<Player>>().single()
        val opponent = api.post("/v1/opponents?clubId=$spaceId") { json(owner, CreateOpponentRequest("Benfica")) }.body<Opponent>()
        val match = api.post("/v1/matches") {
            json(owner, CreateMatchRequest(setup.team.id, opponent.id, dateTime = Clock.System.now(), type = MatchType.FRIENDLY, venue = MatchVenue.HOME))
        }.body<Match>()
        assertFalse(api.get("/v1/matches/${match.id}") { bearerAuth(owner) }.body<MatchDetails>().statsInPlan)
        for (path in listOf(
            "/v1/teams/${setup.team.id}/season-record", "/v1/teams/${setup.team.id}/full-stats",
            "/v1/age-groups/${setup.ageGroup.id}/season-record", "/v1/age-groups/${setup.ageGroup.id}/full-stats",
            "/v1/players/${player.id}/season-stats"
        )) {
            assertTrue(api.get(path) { bearerAuth(owner) }.isPlanLimit(), path)
        }

        // Basic: stats.
        api.setPlan(spaceId, PlanLimits.BASIC)
        assertTrue(api.get("/v1/matches/${match.id}") { bearerAuth(owner) }.body<MatchDetails>().statsInPlan)
        assertEquals(HttpStatusCode.OK, api.get("/v1/teams/${setup.team.id}/season-record") { bearerAuth(owner) }.status)
    }

    @Test
    fun belowProALiveGameIsListedAsNotStarted() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val setup = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 4), "Sub-12 A"))
        }.body<SpaceSetup>()
        val spaceId = setup.space.id
        val ids = api.post("/v1/players") {
            json(owner, CreatePlayersRequest(spaceId, (1..4).map { CreatePlayer("Player", "$it", LocalDate(2015, 1, it)) }, ageGroupId = setup.ageGroup.id))
        }.body<List<Player>>().map { it.id }
        api.post("/v1/age-groups/${setup.ageGroup.id}/players") { json(owner, AddPlayersToAgeGroupRequest(ids)) }
        val opponent = api.post("/v1/opponents?clubId=$spaceId") { json(owner, CreateOpponentRequest("Benfica")) }.body<Opponent>()
        val match = api.post("/v1/matches") {
            json(owner, CreateMatchRequest(setup.team.id, opponent.id, dateTime = Clock.System.now(), type = MatchType.FRIENDLY, venue = MatchVenue.HOME))
        }.body<Match>()
        api.post("/v1/matches/${match.id}/call-up") { json(owner, ManageCallUpRequest(ids)) }
        assertEquals(HttpStatusCode.NoContent, api.post("/v1/matches/${match.id}/start") { json(owner, StartMatchRequest(ids, halfLengthMinutes = 20)) }.status)
        api.post("/v1/matches/${match.id}/events") { json(owner, CreateMatchEventRequest(EventType.GOAL, minute = 3, playerId = ids.first())) }
        suspend fun ageGroup() = api.get("/v1/age-groups/${setup.ageGroup.id}/details") { bearerAuth(owner) }.body<AgeGroupDetails>()
        suspend fun team() = api.get("/v1/teams/${setup.team.id}/details") { bearerAuth(owner) }.body<TeamDetails>()

        // Free and Basic: listed as before kickoff, no score. The game's own screen is still live.
        for (plan in listOf(PlanLimits.FREE, PlanLimits.BASIC)) {
            api.setPlan(spaceId, plan)
            assertEquals(MatchStatus.SCHEDULED, ageGroup().upcomingMatches.single().status)
            assertTrue(ageGroup().upcomingMatchScores.isEmpty())
            assertEquals(MatchStatus.SCHEDULED, team().upcomingMatches.single().status)
            assertTrue(team().upcomingMatchScores.isEmpty())
            assertEquals(MatchStatus.LIVE, api.get("/v1/matches/${match.id}") { bearerAuth(owner) }.body<MatchDetails>().match.status)
        }

        // Pro: live, with the score.
        api.setPlan(spaceId, PlanLimits.PRO)
        assertEquals(MatchStatus.LIVE, ageGroup().upcomingMatches.single().status)
        assertEquals(1, ageGroup().upcomingMatchScores.getValue(match.id).ourScore)
        assertEquals(1, team().upcomingMatchScores.getValue(match.id).ourScore)

        // Finished, Free shows the result.
        api.setPlan(spaceId, PlanLimits.FREE)
        api.patch("/v1/matches/${match.id}/status") { json(owner, UpdateMatchStatusRequest(MatchStatus.FINISHED)) }
        assertEquals(1, ageGroup().recentResults.single().ourScore)
    }

    @Test
    fun goingDownLocksWhatsOverTheLimitAndGoingUpBringsItBack() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val setup = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015), "Sub-12 A"))
        }.body<SpaceSetup>()
        val spaceId = setup.space.id
        val sub12 = setup.ageGroup.id
        suspend fun spaces(token: String) = api.get("/v1/spaces") { bearerAuth(token) }.body<List<SpaceAgeGroups>>().single()
        suspend fun details(token: String, ageGroupId: String) = api.get("/v1/age-groups/$ageGroupId/details") { bearerAuth(token) }

        // Basic: three age groups a season, three people on each.
        api.setPlan(spaceId, PlanLimits.BASIC)
        val sub14 = api.ageGroup(owner, spaceId, "Sub-14", 2013).body<List<AgeGroup>>().single().id
        val sub16 = api.ageGroup(owner, spaceId, "Sub-16", 2011).body<List<AgeGroup>>().single().id
        assertTrue(api.ageGroup(owner, spaceId, "Sub-18", 2009).isPlanLimit())
        val first = api.signUp()
        val second = api.signUp()
        for (coach in listOf(first, second)) {
            val code = api.post("/v1/age-groups/$sub12/invites") { bearerAuth(owner) }.body<StaffInvite>().code
            assertEquals(HttpStatusCode.OK, api.post("/v1/invites/$code/accept") { bearerAuth(coach) }.status)
        }
        assertTrue(api.post("/v1/age-groups/$sub12/invites") { bearerAuth(owner) }.isPlanLimit())
        // What the app shows: Sub-12 full on Basic, Sub-14 with room, no age groups left this season.
        assertEquals(PlanLimits.BASIC, details(owner, sub12).body<AgeGroupDetails>().staffFullOnPlan)
        assertEquals(null, details(owner, sub14).body<AgeGroupDetails>().staffFullOnPlan)
        assertEquals(0, spaces(owner).ageGroupsLeft)

        // Down to Free: the first age group stays, the owner alone on it.
        api.setPlan(spaceId, PlanLimits.FREE)
        assertEquals(setOf(sub14, sub16), spaces(owner).lockedAgeGroupIds.toSet())
        // The Plan card's count is every age group this season, locked ones included; staff get none.
        assertEquals(3, spaces(owner).ageGroupsThisSeason)
        assertEquals(0, spaces(first).ageGroupsThisSeason)
        assertEquals(HttpStatusCode.OK, details(owner, sub12).status)
        assertTrue(details(owner, sub14).isPlanLimit())
        assertTrue(details(first, sub12).isPlanLimit())
        assertEquals(listOf(sub12), spaces(first).lockedAgeGroupIds)

        // Basic again: everything is back.
        api.setPlan(spaceId, PlanLimits.BASIC)
        assertTrue(spaces(owner).lockedAgeGroupIds.isEmpty())
        // Pro: no limit on age groups, nor on staff.
        api.setPlan(spaceId, PlanLimits.PRO)
        assertEquals(null, spaces(owner).ageGroupsLeft)
        assertEquals(null, details(owner, sub12).body<AgeGroupDetails>().staffFullOnPlan)
        api.setPlan(spaceId, PlanLimits.BASIC)
        assertEquals(HttpStatusCode.OK, details(owner, sub16).status)
        assertEquals(HttpStatusCode.OK, details(second, sub12).status)
    }

    @Test
    fun thePlansAreTheServers() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val setup = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015), "Sub-12 A"))
        }.body<SpaceSetup>()

        // Cheapest first, each with its name, all it has and its limits, and the coach's own plan.
        val response = api.get("/v1/plans") { bearerAuth(owner) }.body<PlansResponse>()
        val plans = response.plans
        assertEquals(listOf(PlanLimits.FREE, PlanLimits.BASIC, PlanLimits.PRO), plans.map { it.plan })
        assertEquals(listOf("Free", "Basic", "Pro"), plans.map { it.name })
        assertEquals(listOf(1, 3, null), plans.map { it.ageGroupLimit })
        assertEquals(listOf(1, 3, null), plans.map { it.peoplePerAgeGroupLimit })
        assertEquals(listOf(false, true, true), plans.map { it.has(PlanFeature.STATS) })
        assertEquals(listOf(false, false, true), plans.map { it.has(PlanFeature.FOLLOWING_LIVE) })
        // Sold on Google Play: the paid plans each a subscription, monthly and yearly.
        assertEquals(
            listOf(emptyList(), listOf("coach_basic/monthly", "coach_basic/yearly"), listOf("coach_pro/monthly", "coach_pro/yearly")),
            plans.map { p -> p.storeProducts.map { "${it.googleProductId}/${it.googleBasePlanId}" } }
        )
        assertEquals(PlanLimits.FREE, response.currentPlan)
        assertEquals("Live game recording", plans.first().features.first().text)
        // In the app's language, Portuguese for "pt".
        val pt = api.get("/v1/plans?language=pt") { bearerAuth(owner) }.body<PlansResponse>().plans
        assertEquals("Gravação de jogos ao vivo", pt.first().features.first().text)
        // Raised: the current plan follows. Someone with no space of their own has none.
        api.setPlan(setup.space.id, PlanLimits.PRO)
        assertEquals(PlanLimits.PRO, api.get("/v1/plans") { bearerAuth(owner) }.body<PlansResponse>().currentPlan)
        assertEquals(null, api.get("/v1/plans") { bearerAuth(api.signUp()) }.body<PlansResponse>().currentPlan)
        api.setPlan(setup.space.id, PlanLimits.FREE)

        // A new Free space: its one age group used, and full with the owner alone.
        val space = api.get("/v1/spaces") { bearerAuth(owner) }.body<List<SpaceAgeGroups>>().single()
        assertEquals(1, space.ageGroupsThisSeason)
        assertEquals(0, space.ageGroupsLeft)
        assertEquals(PlanLimits.FREE, api.get("/v1/age-groups/${setup.ageGroup.id}/details") { bearerAuth(owner) }.body<AgeGroupDetails>().staffFullOnPlan)
    }

    @Test
    fun onlyAPlatformAdminSetsPlans() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val spaceId = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015), "Sub-12 A"))
        }.body<SpaceSetup>().space.id

        assertEquals(HttpStatusCode.Forbidden, api.put("/v1/admin/spaces/$spaceId/plan") { json(owner, SetSpacePlanRequest(PlanLimits.PRO)) }.status)
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1/admin/spaces") { bearerAuth(owner) }.status)

        // Only one of the server's plans.
        assertEquals(HttpStatusCode.BadRequest, api.put("/v1/admin/spaces/$spaceId/plan") { json(api.signUp(PLATFORM_ADMIN), SetSpacePlanRequest(SpacePlan("GOLD"))) }.status)
        api.setPlan(spaceId, PlanLimits.PRO)
        val listed = api.get("/v1/admin/spaces") { bearerAuth(api.signUp(PLATFORM_ADMIN)) }.body<List<AdminSpace>>().single { it.id == spaceId }
        assertEquals(PlanLimits.PRO, listed.plan)
        assertTrue(listed.ownerEmail.orEmpty().endsWith("@test.com"))
    }

    @Test
    fun locallyAPlanAskedForIsSetAtOnce() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val spaceId = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015), "Sub-12 A"))
        }.body<SpaceSetup>().space.id
        suspend fun ask(token: String, plan: SpacePlan) = api.post("/v1/plans/request?language=pt") { json(token, RequestPlanRequest(plan)) }

        // Only an owner, for one of the server's plans, other than theirs.
        assertEquals(HttpStatusCode.Forbidden, ask(api.signUp(), PlanLimits.PRO).status)
        assertEquals(HttpStatusCode.BadRequest, ask(owner, SpacePlan("GOLD")).status)
        assertEquals(HttpStatusCode.BadRequest, ask(owner, PlanLimits.FREE).status)

        // A local server changes plans at once, and checks no store purchases.
        assertEquals(PlanChange.INSTANT, api.get("/v1/plans") { bearerAuth(owner) }.body<PlansResponse>().changeBy)
        assertEquals(HttpStatusCode.BadRequest, api.post("/v1/plans/purchases/google") { json(owner, GooglePurchaseRequest("coach_pro", "token")) }.status)
        // Without the secret, Google's notifications are refused.
        assertEquals(HttpStatusCode.Forbidden, api.post("/v1/store/google/notifications?token=guess").status)

        // The tests' server isn't deployed: set there and then, and the plans come back as GET /v1/plans sends them.
        val plans = ask(owner, PlanLimits.PRO).body<PlansResponse>()
        assertEquals(PlanLimits.PRO, plans.currentPlan)
        assertEquals(null, plans.requestedPlan)
        assertEquals("Gravação de jogos ao vivo", plans.plans.first().features.first().text)
        assertEquals(PlanLimits.PRO, api.get("/v1/spaces") { bearerAuth(owner) }.body<List<SpaceAgeGroups>>().single { it.space.id == spaceId }.plan)
    }
}
