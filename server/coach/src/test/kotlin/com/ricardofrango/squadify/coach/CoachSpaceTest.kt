package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.domain.PlanLimits
import com.ricardofrango.squadify.coach.domain.CoachSeasons
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.CreatePlayer
import com.ricardofrango.squadify.model.CreatePlayersRequest
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerProfile
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
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

        assertTrue(api.get("/v1/spaces") { bearerAuth(owner) }.body<List<SpaceAgeGroups>>().isEmpty())

        val setUp = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 7), "Sub-12 A"))
        }
        assertEquals(HttpStatusCode.Created, setUp.status)
        val setup = setUp.body<SpaceSetup>()
        val (start, end) = CoachSeasons.datesAround(Clock.System.todayIn(TimeZone.UTC))
        assertEquals(start to end, setup.season.startDate to setup.season.endDate)
        assertEquals(7, setup.team.teamSize)
        val spaceId = setup.space.id
        // More than one age group: on Pro.
        api.setPlan(spaceId, PlanLimits.PRO)

        // One space of their own.
        assertEquals(
            HttpStatusCode.Conflict,
            api.post("/v1/space") { json(owner, SetUpSpaceRequest("Again", SportType.FOOTBALL, AgeGroupItemRequest("Sub-10", 2017, 2017), "A")) }.status
        )
        assertTrue(api.get("/v1/spaces") { bearerAuth(owner) }.body<List<SpaceAgeGroups>>().single().isOwner)

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

        // The coach app's way: created straight into the age group, born in its year or later only.
        val straightIn = api.post("/v1/players") {
            json(owner, CreatePlayersRequest(spaceId, listOf(CreatePlayer("Tiago", "Sousa", LocalDate(2016, 2, 3))), ageGroupId = setup.ageGroup.id, addToAgeGroup = true))
        }
        assertEquals(HttpStatusCode.Created, straightIn.status)
        val tiago = straightIn.body<List<Player>>().single()
        val tooOld = api.post("/v1/players") {
            json(owner, CreatePlayersRequest(spaceId, listOf(CreatePlayer("Old", "Er", LocalDate(2014, 12, 31))), ageGroupId = setup.ageGroup.id, addToAgeGroup = true))
        }
        assertEquals(HttpStatusCode.BadRequest, tooOld.status)
        val pool = api.get("/v1/age-groups/${setup.ageGroup.id}/details") { bearerAuth(owner) }.body<AgeGroupDetails>().players.map { it.id }
        assertEquals(setOf(player.id, tiago.id), pool.toSet())

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
        assertTrue(api.get("/v1/spaces") { bearerAuth(outsider) }.body<List<SpaceAgeGroups>>().isEmpty())
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
        assertTrue(api.get("/v1/spaces") { bearerAuth(owner) }.body<List<SpaceAgeGroups>>().isEmpty())
    }
}
