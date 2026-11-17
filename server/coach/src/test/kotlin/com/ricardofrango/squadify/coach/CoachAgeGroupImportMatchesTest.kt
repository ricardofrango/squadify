package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.di.coachModule
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.domain.usecase.ImportAgeGroupUseCase
import com.ricardofrango.squadify.model.AgeGroupBundle
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.ExportGrantResponse
import com.ricardofrango.squadify.model.ManageCallUpRequest
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchRecordEvent
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SaveMatchRecordRequest
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.coach.domain.PlanLimits
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.koin.dsl.koinApplication
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

// A game whose result was typed in after it was played (TECHNICAL_DECISIONS §81): its players' minutes aren't in
// any line-up or substitution, only in the stats typed in. The copy must keep them.
class CoachAgeGroupImportMatchesTest {

    @Test
    fun aResultTypedInAfterTheGameKeepsItsMinutesWhenCopied() = testApplication {
        application { module() }
        val api = jsonClient()

        // Ana's space: one player called up to a game played yesterday, its result typed in with minutes.
        val ana = api.signUp()
        val setup = api.post("/v1/space") {
            json(ana, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 4), "Sub-12 A"))
        }.body<SpaceSetup>()
        val rui = api.post("/v1/players") {
            json(ana, CreatePlayersRequest(setup.space.id, listOf(CreatePlayer("Rui", "Costa", LocalDate(2015, 4, 2))), ageGroupId = setup.ageGroup.id, addToAgeGroup = true))
        }.body<List<Player>>().single()
        val opponent = api.post("/v1/opponents?clubId=${setup.space.id}") { json(ana, CreateOpponentRequest("Benfica")) }.body<Opponent>()
        val match = api.post("/v1/matches") {
            json(ana, CreateMatchRequest(setup.team.id, opponent.id, dateTime = Clock.System.now() - 1.days, type = MatchType.FRIENDLY, venue = MatchVenue.HOME))
        }.body<Match>()
        assertEquals(HttpStatusCode.OK, api.post("/v1/matches/${match.id}/call-up") { json(ana, ManageCallUpRequest(listOf(rui.id))) }.status)
        val saved = api.put("/v1/matches/${match.id}/record") {
            json(ana, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.GOAL, minute = 10, playerId = rui.id)), halfLengthMinutes = 20, minutesPlayed = mapOf(rui.id to 35)))
        }
        assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())

        val grant = api.post("/v1/export/grants") { bearerAuth(ana) }.body<ExportGrantResponse>()
        val bundle = api.get("/v1/export/age-groups/${setup.ageGroup.id}/bundle") { header("X-Export-Grant", grant.token) }.body<AgeGroupBundle>()
        assertTrue(bundle.matches.single().enteredAfterGame, "the bundle says it was entered after the game")

        // Ben imports it.
        val ben = api.signUp()
        val benSpace = api.post("/v1/space") {
            json(ben, SetUpSpaceRequest("Ben's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-10", 2016, 2016, teamSize = 4), "Sub-10 A"))
        }.body<SpaceSetup>().space.id
        api.setPlan(benSpace, PlanLimits.PRO)
        val benId = api.get("/v1/users/me") { bearerAuth(ben) }.body<com.ricardofrango.squadify.model.User>().id

        val koin = koinApplication { modules(coachModule) }.koin
        val result = koin.get<ImportAgeGroupUseCase>()(benId, benSpace, "https://ana.example", bundle)
        assertEquals(1, result.matches)

        // The copy is still a game entered after it was played, finished, and Rui has his 35 minutes and his goal.
        val (copy, lines) = koin.get<TransactionManager>().runInTransaction {
            val copy = koin.get<MatchRepository>().getMatchesByClub(benSpace).single()
            copy to koin.get<MatchPlayerStatsRepository>().getByMatch(copy.id)
        }
        assertTrue(copy.enteredAfterGame, "the copy lost enteredAfterGame")
        assertEquals(40, copy.fullTimeMinute, "the copy lost its full time")
        assertEquals(20, copy.firstHalfEndMinute, "the copy lost the end of its first half")
        val line = lines.single()
        assertEquals(35, line.minutesPlayed, "the copy lost the minutes typed in")
        assertEquals(1, line.goals)
    }
}
