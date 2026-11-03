package com.ricardofrango.squadify.coach

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
import com.ricardofrango.squadify.model.Player
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
        assertEquals(SpacePlan.FREE, api.get("/v1/spaces") { bearerAuth(owner) }.body<List<SpaceAgeGroups>>().single().plan)

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
        api.setPlan(spaceId, SpacePlan.BASIC)
        assertTrue(api.get("/v1/matches/${match.id}") { bearerAuth(owner) }.body<MatchDetails>().statsInPlan)
        assertEquals(HttpStatusCode.OK, api.get("/v1/teams/${setup.team.id}/season-record") { bearerAuth(owner) }.status)
    }

    @Test
    fun freeListsALiveGameAsNotStarted() = testApplication {
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

        // Free: listed as before kickoff, no score. The game's own screen is still live.
        assertEquals(MatchStatus.SCHEDULED, ageGroup().upcomingMatches.single().status)
        assertTrue(ageGroup().upcomingMatchScores.isEmpty())
        assertEquals(MatchStatus.SCHEDULED, team().upcomingMatches.single().status)
        assertTrue(team().upcomingMatchScores.isEmpty())
        assertEquals(MatchStatus.LIVE, api.get("/v1/matches/${match.id}") { bearerAuth(owner) }.body<MatchDetails>().match.status)

        // Basic: live, with the score.
        api.setPlan(spaceId, SpacePlan.BASIC)
        assertEquals(MatchStatus.LIVE, ageGroup().upcomingMatches.single().status)
        assertEquals(1, ageGroup().upcomingMatchScores.getValue(match.id).ourScore)
        assertEquals(1, team().upcomingMatchScores.getValue(match.id).ourScore)

        // Finished, Free shows the result.
        api.setPlan(spaceId, SpacePlan.FREE)
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
        api.setPlan(spaceId, SpacePlan.BASIC)
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

        // Down to Free: the first age group stays, the owner alone on it.
        api.setPlan(spaceId, SpacePlan.FREE)
        assertEquals(setOf(sub14, sub16), spaces(owner).lockedAgeGroupIds.toSet())
        // The Plan card's count is every age group this season, locked ones included; staff get none.
        assertEquals(3, spaces(owner).ageGroupsThisSeason)
        assertEquals(0, spaces(first).ageGroupsThisSeason)
        assertEquals(HttpStatusCode.OK, details(owner, sub12).status)
        assertTrue(details(owner, sub14).isPlanLimit())
        assertTrue(details(first, sub12).isPlanLimit())
        assertEquals(listOf(sub12), spaces(first).lockedAgeGroupIds)

        // Basic again: everything is back.
        api.setPlan(spaceId, SpacePlan.BASIC)
        assertTrue(spaces(owner).lockedAgeGroupIds.isEmpty())
        assertEquals(HttpStatusCode.OK, details(owner, sub16).status)
        assertEquals(HttpStatusCode.OK, details(second, sub12).status)
    }

    @Test
    fun onlyAPlatformAdminSetsPlans() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val spaceId = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015), "Sub-12 A"))
        }.body<SpaceSetup>().space.id

        assertEquals(HttpStatusCode.Forbidden, api.put("/v1/admin/spaces/$spaceId/plan") { json(owner, SetSpacePlanRequest(SpacePlan.PRO)) }.status)
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1/admin/spaces") { bearerAuth(owner) }.status)

        api.setPlan(spaceId, SpacePlan.PRO)
        val listed = api.get("/v1/admin/spaces") { bearerAuth(api.signUp(PLATFORM_ADMIN)) }.body<List<AdminSpace>>().single { it.id == spaceId }
        assertEquals(SpacePlan.PRO, listed.plan)
        assertTrue(listed.ownerEmail.orEmpty().endsWith("@test.com"))
    }
}
