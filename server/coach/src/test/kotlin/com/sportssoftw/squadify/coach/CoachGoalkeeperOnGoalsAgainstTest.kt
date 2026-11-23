package com.sportssoftw.squadify.coach

import com.sportssoftw.squadify.coach.di.coachModule
import com.sportssoftw.squadify.coach.domain.PlanLimits
import com.sportssoftw.squadify.domain.repository.MatchEventRepository
import com.sportssoftw.squadify.domain.repository.MatchPlayerStatsRepository
import com.sportssoftw.squadify.domain.repository.MatchRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.domain.usecase.ImportAgeGroupUseCase
import com.sportssoftw.squadify.model.AgeGroupBundle
import com.sportssoftw.squadify.model.AgeGroupItemRequest
import com.sportssoftw.squadify.model.CreateMatchRequest
import com.sportssoftw.squadify.model.CreateOpponentRequest
import com.sportssoftw.squadify.model.CreatePlayer
import com.sportssoftw.squadify.model.CreatePlayersRequest
import com.sportssoftw.squadify.model.EventType
import com.sportssoftw.squadify.model.ExportGrantResponse
import com.sportssoftw.squadify.model.ManageCallUpRequest
import com.sportssoftw.squadify.model.Match
import com.sportssoftw.squadify.model.MatchEvent
import com.sportssoftw.squadify.model.MatchRecordEvent
import com.sportssoftw.squadify.model.MatchType
import com.sportssoftw.squadify.model.MatchVenue
import com.sportssoftw.squadify.model.Opponent
import com.sportssoftw.squadify.model.Player
import com.sportssoftw.squadify.model.SaveMatchRecordRequest
import com.sportssoftw.squadify.model.SetUpSpaceRequest
import com.sportssoftw.squadify.model.SpaceSetup
import com.sportssoftw.squadify.model.SportType
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

// The goalkeeper who suffered a goal by the opponent (TECHNICAL_DECISIONS §86), end to end on the coach server:
// picked in the record of a game entered afterwards, listed with its events, and kept when the age group is copied.
class CoachGoalkeeperOnGoalsAgainstTest {

    @Test
    fun aGoalAgainstUsKeepsTheGoalkeeperPickedAndItSurvivesTheCopy() = testApplication {
        application { module() }
        val api = jsonClient()

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

        // A goalkeeper who wasn't called up is refused; a goalkeeper on our own goal too.
        val stranger = "00000000-0000-0000-0000-000000000001"
        assertEquals(
            HttpStatusCode.BadRequest,
            api.put("/v1/matches/${match.id}/record") {
                json(ana, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.GOAL, minute = 15, isOpponent = true, goalkeeperPlayerId = stranger)), halfLengthMinutes = 20, minutesPlayed = mapOf(rui.id to 35)))
            }.status
        )

        val saved = api.put("/v1/matches/${match.id}/record") {
            json(ana, SaveMatchRecordRequest(listOf(MatchRecordEvent(type = EventType.GOAL, minute = 15, isOpponent = true, goalkeeperPlayerId = rui.id)), halfLengthMinutes = 20, minutesPlayed = mapOf(rui.id to 35)))
        }
        assertEquals(HttpStatusCode.OK, saved.status, saved.bodyAsText())

        // The goal lists its goalkeeper with the match's events.
        val events = api.get("/v1/matches/${match.id}/events") { bearerAuth(ana) }.body<List<MatchEvent>>()
        assertEquals(rui.id, events.single { it.isOpponent }.goalkeeperPlayerId)

        // Rui's stats line counts it, and so does the Full stats table the apps read (stats are on Basic and Pro).
        api.setPlan(setup.space.id, PlanLimits.PRO)
        val full = api.get("/v1/age-groups/${setup.ageGroup.id}/full-stats") { bearerAuth(ana) }.body<com.sportssoftw.squadify.model.FullStatsData>()
        assertEquals(1, full.lines.single { it.playerId == rui.id }.goalsConceded)

        // Copied into Ben's space, the goal keeps its goalkeeper (the copy's own Rui).
        val grant = api.post("/v1/export/grants") { bearerAuth(ana) }.body<ExportGrantResponse>()
        val bundle = api.get("/v1/export/age-groups/${setup.ageGroup.id}/bundle") { header("X-Export-Grant", grant.token) }.body<AgeGroupBundle>()
        assertEquals(rui.id, bundle.matches.single().events.single { it.isOpponent }.goalkeeperPlayerId)

        val ben = api.signUp()
        val benSpace = api.post("/v1/space") {
            json(ben, SetUpSpaceRequest("Ben's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-10", 2016, 2016, teamSize = 4), "Sub-10 A"))
        }.body<SpaceSetup>().space.id
        api.setPlan(benSpace, PlanLimits.PRO)
        val benId = api.get("/v1/users/me") { bearerAuth(ben) }.body<com.sportssoftw.squadify.model.User>().id

        val koin = koinApplication { modules(coachModule) }.koin
        koin.get<ImportAgeGroupUseCase>()(benId, benSpace, "https://ana.example", bundle)
        val (copiedGoal, copiedLine) = koin.get<TransactionManager>().runInTransaction {
            val copy = koin.get<MatchRepository>().getMatchesByClub(benSpace).single()
            koin.get<MatchEventRepository>().getEventsByMatch(copy.id).single { it.isOpponent } to
                koin.get<MatchPlayerStatsRepository>().getByMatch(copy.id).single()
        }
        assertEquals(copiedLine.playerId, copiedGoal.goalkeeperPlayerId, "the copied goal lost its goalkeeper")
        assertEquals(1, copiedLine.goalsConceded, "the copy's stats line lost the goal conceded")
    }
}
