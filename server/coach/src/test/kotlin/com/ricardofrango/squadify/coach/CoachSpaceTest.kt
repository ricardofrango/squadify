package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.domain.CoachSeasons
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerProfile
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

// The coach's space end to end, on the coach database: the first setup, the season picked by itself,
// the structure routes on the coach's policy, and someone from outside the space refused.
class CoachSpaceTest {

    @Test
    fun firstSetupThenTheStructure() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()

        assertEquals(HttpStatusCode.NotFound, api.get("/v1/space") { bearerAuth(owner) }.status)

        val setUp = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 7), "Sub-12 A"))
        }
        assertEquals(HttpStatusCode.Created, setUp.status)
        val setup = setUp.body<SpaceSetup>()
        val (start, end) = CoachSeasons.datesAround(Clock.System.todayIn(TimeZone.UTC))
        assertEquals(start to end, setup.season.startDate to setup.season.endDate)
        assertEquals(7, setup.team.teamSize)
        val spaceId = setup.space.id

        // One space per account for now.
        assertEquals(
            HttpStatusCode.Conflict,
            api.post("/v1/space") { json(owner, SetUpSpaceRequest("Again", SportType.FOOTBALL, AgeGroupItemRequest("Sub-10", 2017, 2017), "A")) }.status
        )
        assertTrue(api.get("/v1/space") { bearerAuth(owner) }.body<CoachSpace>().isOwner)

        // A second age group, with no season given: the current one.
        val created = api.post("/v1/age-groups") {
            json(owner, CreateAgeGroupsRequest(spaceId, items = listOf(AgeGroupItemRequest("Sub-14", 2013, 2013))))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(setup.season.id, created.body<List<AgeGroup>>().single().seasonId)

        val details = api.get("/v1/age-groups/${setup.ageGroup.id}/details") { bearerAuth(owner) }.body<AgeGroupDetails>()
        assertTrue(details.canCreateTeam && details.canAddStaff && details.canAssignPlayersToAgeGroup)

        // A player, into the pool, and their profile (no parent accounts here).
        val player = api.post("/v1/players") {
            json(owner, CreatePlayersRequest(spaceId, listOf(CreatePlayer("Rui", "Costa", LocalDate(2015, 4, 2))), ageGroupId = setup.ageGroup.id))
        }.body<List<Player>>().single()
        assertEquals(
            HttpStatusCode.Created,
            api.post("/v1/age-groups/${setup.ageGroup.id}/players") { json(owner, AddPlayersToAgeGroupRequest(listOf(player.id))) }.status
        )
        assertEquals(0, api.get("/v1/players/${player.id}") { bearerAuth(owner) }.body<PlayerProfile>().guardiansLinked)

        // Emails would be invites, which the coach product doesn't have.
        val withEmail = api.post("/v1/players") {
            json(owner, CreatePlayersRequest(spaceId, listOf(CreatePlayer("Rita", "Lopes", LocalDate(2015, 1, 9), email = "rita@example.com"))))
        }
        assertEquals(HttpStatusCode.BadRequest, withEmail.status)

        assertEquals(
            HttpStatusCode.Created,
            api.post("/v1/opponents?clubId=$spaceId") { json(owner, CreateOpponentRequest("Benfica")) }.status
        )
        assertEquals(HttpStatusCode.NoContent, api.delete("/v1/teams/${setup.team.id}") { bearerAuth(owner) }.status)

        // Someone outside the space sees and creates nothing in it.
        val outsider = api.signUp()
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1/age-groups/${setup.ageGroup.id}/details") { bearerAuth(outsider) }.status)
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1/age-groups?clubId=$spaceId") { bearerAuth(outsider) }.status)
        assertEquals(
            HttpStatusCode.Forbidden,
            api.post("/v1/age-groups") { json(outsider, CreateAgeGroupsRequest(spaceId, items = listOf(AgeGroupItemRequest("Sub-16", 2011, 2011)))) }.status
        )
        assertFalse(api.get("/v1/space") { bearerAuth(outsider) }.status == HttpStatusCode.OK)
    }

    @Test
    fun anUnknownSportSizeIsRefusedAndNothingIsCreated() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val refused = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Futsal", SportType.FUTSAL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 7), "A"))
        }
        assertEquals(HttpStatusCode.BadRequest, refused.status)
        assertEquals(HttpStatusCode.NotFound, api.get("/v1/space") { bearerAuth(owner) }.status)
    }
}
