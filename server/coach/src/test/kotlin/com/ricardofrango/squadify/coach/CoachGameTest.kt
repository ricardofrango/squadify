package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CreateMatchEventRequest
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.FullStatsData
import com.ricardofrango.squadify.model.ManageCallUpRequest
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.StartMatchRequest
import com.ricardofrango.squadify.model.TeamSeasonRecord
import com.ricardofrango.squadify.model.UpdateMatchStatusRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

// A game on the coach server, through the routes shared with the clubs server: scheduled, called up,
// kicked off, a goal, full time, then its season record and full stats. Someone from outside the space
// can't open it.
class CoachGameTest {

    @Test
    fun aGameFromSchedulingToStats() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()

        // 4-a-side, so four players make a line-up.
        val setup = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 4), "Sub-12 A"))
        }.body<SpaceSetup>()
        val spaceId = setup.space.id
        // More than one age group, staff or stats: on Pro.
        api.setPlan(spaceId, SpacePlan.PRO)
        val players = api.post("/v1/players") {
            json(owner, CreatePlayersRequest(spaceId, (1..4).map { CreatePlayer("Player", "$it", LocalDate(2015, 1, it)) }, ageGroupId = setup.ageGroup.id))
        }.body<List<Player>>()
        val ids = players.map { it.id }
        api.post("/v1/age-groups/${setup.ageGroup.id}/players") { json(owner, AddPlayersToAgeGroupRequest(ids)) }
        val opponent = api.post("/v1/opponents?clubId=$spaceId") { json(owner, CreateOpponentRequest("Benfica")) }.body<Opponent>()

        val scheduled = api.post("/v1/matches") {
            json(owner, CreateMatchRequest(setup.team.id, opponent.id, dateTime = Clock.System.now(), type = MatchType.FRIENDLY, venue = MatchVenue.HOME))
        }
        assertEquals(HttpStatusCode.Created, scheduled.status)
        val match = scheduled.body<Match>()

        assertEquals(HttpStatusCode.OK, api.post("/v1/matches/${match.id}/call-up") { json(owner, ManageCallUpRequest(ids)) }.status)
        assertEquals(
            HttpStatusCode.NoContent,
            api.post("/v1/matches/${match.id}/start") { json(owner, StartMatchRequest(ids, halfLengthMinutes = 20)) }.status
        )
        assertEquals(
            HttpStatusCode.Created,
            api.post("/v1/matches/${match.id}/events") { json(owner, CreateMatchEventRequest(EventType.GOAL, minute = 3, playerId = ids.first())) }.status
        )
        assertEquals(1, api.get("/v1/matches/${match.id}/events/score") { bearerAuth(owner) }.body<MatchScore>().ourScore)
        assertEquals(
            HttpStatusCode.NoContent,
            api.patch("/v1/matches/${match.id}/status") { json(owner, UpdateMatchStatusRequest(MatchStatus.FINISHED)) }.status
        )

        assertEquals(match.id, api.get("/v1/matches?clubId=$spaceId") { bearerAuth(owner) }.body<List<Match>>().single().id)
        assertEquals(
            listOf(match.id),
            api.get("/v1/teams/${setup.team.id}/season-record") { bearerAuth(owner) }.body<TeamSeasonRecord>().matches.map { it.matchId }
        )
        // Full stats, with no trainings: the coach product has none.
        assertEquals(HttpStatusCode.OK, api.get("/v1/age-groups/${setup.ageGroup.id}/full-stats") { bearerAuth(owner) }.status)
        assertEquals(setup.ageGroup.id, api.get("/v1/teams/${setup.team.id}/full-stats") { bearerAuth(owner) }.body<FullStatsData>().ageGroupId)
        assertEquals(HttpStatusCode.OK, api.get("/v1/players/${ids.first()}/season-stats") { bearerAuth(owner) }.status)

        val outsider = api.signUp()
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1/matches/${match.id}") { bearerAuth(outsider) }.status)
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1/matches?clubId=$spaceId") { bearerAuth(outsider) }.status)
    }
}
