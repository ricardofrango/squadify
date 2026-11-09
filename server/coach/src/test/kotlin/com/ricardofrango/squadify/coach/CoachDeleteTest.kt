package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.domain.PlanLimits
import com.ricardofrango.squadify.model.AddAgeGroupsRequest
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AddedAgeGroups
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.CreateMatchRequest
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.MatchVenue
import com.ricardofrango.squadify.model.NewAgeGroup
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.OpponentDetails
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.SpaceDetails
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.StaffInvite
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

// Deleting an opponent (with its games) and an age group (with its teams, games and lone players): the
// space's owner's, not its staff's.
class CoachDeleteTest {

    @Test
    fun theOwnerDeletesAnOpponentAndAnAgeGroupWithWhatsInThem() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()

        val added = api.post("/v1/space/age-groups") {
            json(owner, AddAgeGroupsRequest("Povoense", listOf(NewAgeGroup("Sub 11", 2016, 2016)), spaceName = "Espaço de Ana Coach", sport = SportType.FOOTBALL))
        }.body<AddedAgeGroups>()
        val spaceId = added.space.id
        api.setPlan(spaceId, PlanLimits.PRO)
        val sub11 = added.ageGroups.single()
        val sub12 = api.post("/v1/space/age-groups") { json(owner, AddAgeGroupsRequest("Povoense", listOf(NewAgeGroup("Sub 12", 2015, 2015)))) }
            .body<AddedAgeGroups>().ageGroups.single()

        // Rui only in Sub 11; Tiago in both.
        val rui = api.post("/v1/players") {
            json(owner, CreatePlayersRequest(spaceId, listOf(CreatePlayer("Rui", "Costa", LocalDate(2016, 3, 1))), ageGroupId = sub11.id, addToAgeGroup = true))
        }.body<List<Player>>().single()
        val tiago = api.post("/v1/players") {
            json(owner, CreatePlayersRequest(spaceId, listOf(CreatePlayer("Tiago", "Sousa", LocalDate(2016, 5, 1))), ageGroupId = sub11.id, addToAgeGroup = true))
        }.body<List<Player>>().single()
        api.post("/v1/age-groups/${sub12.id}/players") { json(owner, AddPlayersToAgeGroupRequest(listOf(tiago.id))) }

        val sub11Team = api.get("/v1/age-groups/${sub11.id}/details") { bearerAuth(owner) }.body<AgeGroupDetails>().teams.single()
        val benfica = api.post("/v1/opponents?clubId=$spaceId") { json(owner, CreateOpponentRequest("Benfica")) }.body<Opponent>()
        val sporting = api.post("/v1/opponents?clubId=$spaceId") { json(owner, CreateOpponentRequest("Sporting")) }.body<Opponent>()
        fun game(opponent: Opponent) = CreateMatchRequest(sub11Team.id, opponent.id, dateTime = Clock.System.now(), type = MatchType.FRIENDLY, venue = MatchVenue.HOME)
        val vsBenfica = api.post("/v1/matches") { json(owner, game(benfica)) }.body<Match>()
        val vsSporting = api.post("/v1/matches") { json(owner, game(sporting)) }.body<Match>()

        // Staff may not delete either.
        val joao = api.signUp()
        val invite = api.post("/v1/age-groups/${sub11.id}/invites") { bearerAuth(owner) }.body<StaffInvite>()
        api.post("/v1/invites/${invite.code}/accept") { bearerAuth(joao) }
        assertFalse(api.get("/v1/opponents/${benfica.id}") { bearerAuth(joao) }.body<OpponentDetails>().canDelete)
        assertEquals(HttpStatusCode.Forbidden, api.delete("/v1/opponents/${benfica.id}") { bearerAuth(joao) }.status)
        assertEquals(HttpStatusCode.Forbidden, api.delete("/v1/age-groups/${sub11.id}") { bearerAuth(joao) }.status)

        // An opponent goes with its games.
        assertTrue(api.get("/v1/opponents/${benfica.id}") { bearerAuth(owner) }.body<OpponentDetails>().canDelete)
        assertEquals(HttpStatusCode.NoContent, api.delete("/v1/opponents/${benfica.id}") { bearerAuth(owner) }.status)
        assertEquals(HttpStatusCode.NotFound, api.get("/v1/opponents/${benfica.id}") { bearerAuth(owner) }.status)
        assertEquals(HttpStatusCode.NotFound, api.get("/v1/matches/${vsBenfica.id}") { bearerAuth(owner) }.status)

        // An age group goes with its teams, games and the players in no other age group.
        assertTrue(api.get("/v1/age-groups/${sub11.id}/details") { bearerAuth(owner) }.body<AgeGroupDetails>().canDeleteAgeGroup)
        assertEquals(HttpStatusCode.NoContent, api.delete("/v1/age-groups/${sub11.id}") { bearerAuth(owner) }.status)
        assertEquals(listOf(sub12.id), api.get("/v1/spaces/$spaceId") { bearerAuth(owner) }.body<SpaceDetails>().ageGroups.map { it.ageGroup.id })
        assertEquals(HttpStatusCode.NotFound, api.get("/v1/matches/${vsSporting.id}") { bearerAuth(owner) }.status)
        assertEquals(HttpStatusCode.NotFound, api.get("/v1/players/${rui.id}") { bearerAuth(owner) }.status)
        assertEquals(HttpStatusCode.OK, api.get("/v1/players/${tiago.id}") { bearerAuth(owner) }.status)
    }
}
