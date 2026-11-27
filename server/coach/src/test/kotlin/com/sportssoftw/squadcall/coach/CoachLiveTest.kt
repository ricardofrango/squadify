package com.sportssoftw.squadcall.coach

import com.sportssoftw.squadcall.coach.domain.PlanLimits
import com.sportssoftw.squadcall.coach.config.CoachConfig
import com.sportssoftw.squadcall.model.AgeGroupItemRequest
import com.sportssoftw.squadcall.model.CreateMatchRequest
import com.sportssoftw.squadcall.model.CreateOpponentRequest
import com.sportssoftw.squadcall.model.LiveMatchUpdate
import com.sportssoftw.squadcall.model.Match
import com.sportssoftw.squadcall.model.MatchStatus
import com.sportssoftw.squadcall.model.MatchType
import com.sportssoftw.squadcall.model.MatchVenue
import com.sportssoftw.squadcall.model.Opponent
import com.sportssoftw.squadcall.model.PLAN_LIMIT_CODE
import com.sportssoftw.squadcall.model.SetUpSpaceRequest
import com.sportssoftw.squadcall.model.SpaceSetup
import com.sportssoftw.squadcall.model.SportType
import com.sportssoftw.squadcall.model.UpdateMatchStatusRequest
import com.sportssoftw.squadcall.security.JwtTokens
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

// The live game's WebSocket, shared with the clubs server: the coach's own token follows the match on
// Pro; below Pro, and for a token from another audience (the clubs app's) or someone outside the space,
// it's closed.
class CoachLiveTest {

    @Test
    fun theOwnerFollowsTheMatchLive() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val match = scheduleMatch(owner)
        api.setPlan(match.clubId, PlanLimits.PRO)
        val sockets = socketClient()

        sockets.webSocket("/v1/matches/${match.id}/live?token=$owner") {
            // Subscribed once the handshake is through; then the match is called off elsewhere.
            delay(300)
            api.patch("/v1/matches/${match.id}/status") { json(owner, UpdateMatchStatusRequest(MatchStatus.CANCELLED)) }
            val update = withTimeout(5_000) { receiveDeserialized<LiveMatchUpdate>() }
            assertEquals(MatchStatus.CANCELLED, update.match?.status)
        }
    }

    @Test
    fun anotherAudiencesTokenAndOutsidersAreClosedOut() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val match = scheduleMatch(owner)
        val sockets = socketClient()

        // Same secret, but the clubs app's audience: never valid here.
        val clubsToken = JwtTokens(CoachConfig.jwtSecret, audience = "club-manager-users").generateAccessToken("anyone")
        sockets.webSocket("/v1/matches/${match.id}/live?token=$clubsToken") {
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, withTimeout(5_000) { closeReason.await() }?.code)
        }
        val outsider = api.signUp()
        sockets.webSocket("/v1/matches/${match.id}/live?token=$outsider") {
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, withTimeout(5_000) { closeReason.await() }?.code)
        }
    }

    @Test
    fun belowProTheSocketsAreClosedForThePlan() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val match = scheduleMatch(owner)
        val sockets = socketClient()

        for (plan in listOf(PlanLimits.FREE, PlanLimits.BASIC)) {
            api.setPlan(match.clubId, plan)
            for (path in listOf("/v1/matches/${match.id}/live", "/v1/clubs/${match.clubId}/matches/live")) {
                sockets.webSocket("$path?token=$owner") {
                    assertEquals(PLAN_LIMIT_CODE, withTimeout(5_000) { closeReason.await() }?.message, "$plan $path")
                }
            }
        }
    }

    private fun ApplicationTestBuilder.socketClient() = createClient {
        install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun ApplicationTestBuilder.scheduleMatch(owner: String): Match {
        val api = jsonClient()
        val setup = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 4), "Sub-12 A"))
        }.body<SpaceSetup>()
        val opponent = api.post("/v1/opponents?clubId=${setup.space.id}") { json(owner, CreateOpponentRequest("Benfica")) }.body<Opponent>()
        return api.post("/v1/matches") {
            json(owner, CreateMatchRequest(setup.team.id, opponent.id, dateTime = Clock.System.now(), type = MatchType.FRIENDLY, venue = MatchVenue.HOME))
        }.body<Match>()
    }
}
