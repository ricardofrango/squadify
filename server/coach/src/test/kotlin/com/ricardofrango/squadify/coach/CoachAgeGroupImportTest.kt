package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.di.coachModule
import com.ricardofrango.squadify.coach.domain.PlanLimits
import com.ricardofrango.squadify.domain.usecase.ImportAgeGroupUseCase
import com.ricardofrango.squadify.model.ImportAgeGroupResult
import com.ricardofrango.squadify.model.AgeGroupBundle
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.ExportGrantResponse
import com.ricardofrango.squadify.model.ExportableAgeGroup
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals
import kotlin.time.Clock
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication

// Copying an age group into another space (TECHNICAL_DECISIONS §81), on one server: Ana's age group is exported
// with a grant (single use), and Ben's space imports it. Ben gets the teams, players, opponent and match; the
// second import of the same age group is refused.
class CoachAgeGroupImportTest {

    @Test
    fun anAgeGroupCopiedFromAnotherSpaceArrivesWithItsContents() = testApplication {
        application { module() }
        val api = jsonClient()

        // Ana's space: Sub-12, one player, an opponent and a scheduled match.
        val ana = api.signUp()
        val setup = api.post("/v1/space") {
            json(ana, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 4), "Sub-12 A"))
        }.body<SpaceSetup>()
        val created = api.post("/v1/players") {
            json(ana, CreatePlayersRequest(setup.space.id, listOf(CreatePlayer("Rui", "Costa", LocalDate(2015, 4, 2))), ageGroupId = setup.ageGroup.id, addToAgeGroup = true))
        }.body<List<Player>>()
        assertEquals(1, created.size, "created: $created")
        val opponent = api.post("/v1/opponents?clubId=${setup.space.id}") { json(ana, CreateOpponentRequest("Benfica")) }.body<Opponent>()
        api.post("/v1/matches") {
            json(ana, CreateMatchRequest(setup.team.id, opponent.id, dateTime = Clock.System.now(), type = MatchType.FRIENDLY, venue = MatchVenue.HOME))
        }.body<Match>()

        // The export: a grant lists the age group, its bundle comes once, and the grant is spent.
        val grantResponse = api.post("/v1/export/grants") { bearerAuth(ana) }
        assertEquals(HttpStatusCode.OK, grantResponse.status, grantResponse.bodyAsText())
        val grant = grantResponse.body<ExportGrantResponse>()
        val exportable = api.get("/v1/export/age-groups") { header("X-Export-Grant", grant.token) }.body<List<ExportableAgeGroup>>()
        assertEquals(setup.ageGroup.id, exportable.single().id)
        val bundle = api.get("/v1/export/age-groups/${setup.ageGroup.id}/bundle") { header("X-Export-Grant", grant.token) }.body<AgeGroupBundle>()
        assertEquals(1, bundle.players.size, "bundle: $bundle")
        assertEquals(1, bundle.matches.size)
        assertEquals(HttpStatusCode.Unauthorized, api.get("/v1/export/age-groups/${setup.ageGroup.id}/bundle") { header("X-Export-Grant", grant.token) }.status)

        // The import into Ben's space (a Pro plan has room for the age group).
        val ben = api.signUp()
        val benSpace = api.post("/v1/space") {
            json(ben, SetUpSpaceRequest("Ben's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-10", 2016, 2016, teamSize = 4), "Sub-10 A"))
        }.body<SpaceSetup>().space.id
        api.setPlan(benSpace, PlanLimits.PRO)
        val benId = api.get("/v1/users/me") { bearerAuth(ben) }.body<com.ricardofrango.squadify.model.User>().id

        val koin = koinApplication { modules(coachModule) }.koin
        try {
            val importUseCase = koin.get<ImportAgeGroupUseCase>()
            val result = importUseCase(benId, benSpace, "https://ana.example", bundle)
            assertEquals(1, result.teams)
            assertEquals(1, result.players)
            assertEquals(1, result.opponents)
            assertEquals(1, result.matches)

            // The copy is in Ben's space, with its player and match.
            val ageGroups = api.get("/v1/age-groups?clubId=$benSpace") { bearerAuth(ben) }.body<List<com.ricardofrango.squadify.model.AgeGroup>>()
            assertEquals(setOf("Sub-10", "Sub-12"), ageGroups.map { it.name }.toSet())

            // Imported again, the same copy is updated: nothing new is created, and there is still one copy.
            val again = importUseCase(benId, benSpace, "https://ana.example", bundle)
            assertEquals(ImportAgeGroupResult(result.ageGroupId, teams = 0, players = 0, opponents = 0, matches = 0), again)
            assertEquals(2, api.get("/v1/age-groups?clubId=$benSpace") { bearerAuth(ben) }.body<List<com.ricardofrango.squadify.model.AgeGroup>>().size)
        } finally {
            stopKoin()
        }
    }
}
