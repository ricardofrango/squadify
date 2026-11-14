package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.DeleteSpaceRequest
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Deleting a space (REQUIREMENTS §5c): only its owner, after their password, and the space goes with
// everything in it. The owner keeps their account.
class CoachSpaceDeletionTest {

    @Test
    fun onlyTheOwnerDeletesTheSpaceAfterTheirPassword() = testApplication {
        application { module() }
        val api = jsonClient()

        val owner = api.signUp()
        val spaceId = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Owner's space", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 4), "Sub-12 A"))
        }.body<SpaceSetup>().space.id

        // A stranger can't delete it, and a wrong password is refused.
        val stranger = api.signUp()
        assertEquals(
            HttpStatusCode.Forbidden,
            api.post("/v1/spaces/$spaceId/delete") { json(stranger, DeleteSpaceRequest("password1")) }.status
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            api.post("/v1/spaces/$spaceId/delete") { json(owner, DeleteSpaceRequest("wrong-one")) }.status
        )

        // The owner, with the right password: gone from their list, and its details can't be read.
        assertEquals(
            HttpStatusCode.NoContent,
            api.post("/v1/spaces/$spaceId/delete") { json(owner, DeleteSpaceRequest("password1")) }.status
        )
        val spaces = api.get("/v1/spaces") { bearerAuth(owner) }.body<List<SpaceAgeGroups>>()
        assertTrue(spaces.none { it.space.id == spaceId }, "the deleted space is still listed")
        assertTrue(api.get("/v1/spaces/$spaceId") { bearerAuth(owner) }.status.value >= 400)

        // The owner keeps their account: they can still sign in.
        assertEquals(HttpStatusCode.OK, api.get("/v1/spaces") { bearerAuth(owner) }.status)
    }
}
