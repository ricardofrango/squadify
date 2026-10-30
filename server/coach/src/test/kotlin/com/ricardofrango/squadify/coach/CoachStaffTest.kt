package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.StaffInvite
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Staff on the coach server end to end: the owner shares a code for one age group, someone uses it
// and becomes staff there only, the code can't be used twice, and the owner can take them off.
class CoachStaffTest {

    @Test
    fun anInviteCodeBringsSomeoneOntoOneAgeGroup() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val setup = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015), "Sub-12 A"))
        }.body<SpaceSetup>()
        val spaceId = setup.space.id
        val sub14 = api.post("/v1/age-groups") {
            json(owner, CreateAgeGroupsRequest(spaceId, items = listOf(AgeGroupItemRequest("Sub-14", 2013, 2013))))
        }.body<List<AgeGroup>>().single()

        val invite = api.post("/v1/age-groups/${setup.ageGroup.id}/invites") { bearerAuth(owner) }.body<StaffInvite>()
        assertTrue(Regex("[A-Z2-9]{3}-[A-Z2-9]{3}").matches(invite.code))
        assertEquals(listOf(invite.code), api.get("/v1/age-groups/${setup.ageGroup.id}/invites") { bearerAuth(owner) }.body<List<StaffInvite>>().map { it.code })

        // Typed in lower case without the dash still works.
        val joao = api.signUp()
        val joined = api.post("/v1/invites/${invite.code.replace("-", "").lowercase()}/accept") { bearerAuth(joao) }
        assertEquals(HttpStatusCode.OK, joined.status)
        assertFalse(joined.body<CoachSpace>().isOwner)
        assertTrue(api.get("/v1/age-groups/${setup.ageGroup.id}/invites") { bearerAuth(owner) }.body<List<StaffInvite>>().isEmpty())

        // João sees Sub-12 only, manages it, and can't create age groups or invite.
        assertEquals(listOf(setup.ageGroup.id), api.get("/v1/age-groups?clubId=$spaceId") { bearerAuth(joao) }.body<List<AgeGroup>>().map { it.id })
        val details = api.get("/v1/age-groups/${setup.ageGroup.id}/details") { bearerAuth(joao) }.body<AgeGroupDetails>()
        assertTrue(details.canCreateTeam && !details.canAddStaff)
        assertEquals(HttpStatusCode.Forbidden, api.get("/v1/age-groups/${sub14.id}/details") { bearerAuth(joao) }.status)
        assertEquals(
            HttpStatusCode.Forbidden,
            api.post("/v1/age-groups") { json(joao, CreateAgeGroupsRequest(spaceId, items = listOf(AgeGroupItemRequest("Sub-16", 2011, 2011)))) }.status
        )
        assertEquals(HttpStatusCode.Forbidden, api.post("/v1/age-groups/${setup.ageGroup.id}/invites") { bearerAuth(joao) }.status)

        // Single-use, and the owner doesn't need one.
        val other = api.signUp()
        assertEquals(HttpStatusCode.Conflict, api.post("/v1/invites/${invite.code}/accept") { bearerAuth(other) }.status)
        val second = api.post("/v1/age-groups/${sub14.id}/invites") { bearerAuth(owner) }.body<StaffInvite>()
        assertEquals(HttpStatusCode.Conflict, api.post("/v1/invites/${second.code}/accept") { bearerAuth(owner) }.status)

        // A second age group for João, then off both: out of the space.
        assertEquals(HttpStatusCode.OK, api.post("/v1/invites/${second.code}/accept") { bearerAuth(joao) }.status)
        val joaoId = api.get("/v1/age-groups/${sub14.id}/details") { bearerAuth(owner) }.body<AgeGroupDetails>().staff.single().userId
        assertEquals(HttpStatusCode.NoContent, api.delete("/v1/age-groups/${sub14.id}/staff/$joaoId") { bearerAuth(owner) }.status)
        assertEquals(HttpStatusCode.OK, api.get("/v1/space") { bearerAuth(joao) }.status)
        assertEquals(HttpStatusCode.NoContent, api.delete("/v1/age-groups/${setup.ageGroup.id}/staff/$joaoId") { bearerAuth(owner) }.status)
        assertEquals(HttpStatusCode.NotFound, api.get("/v1/space") { bearerAuth(joao) }.status)
    }

    @Test
    fun theOwnerRevokesAnUnusedCode() = testApplication {
        application { module() }
        val api = jsonClient()
        val owner = api.signUp()
        val setup = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Ana's team", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015), "Sub-12 A"))
        }.body<SpaceSetup>()
        val invite = api.post("/v1/age-groups/${setup.ageGroup.id}/invites") { bearerAuth(owner) }.body<StaffInvite>()

        assertEquals(HttpStatusCode.NoContent, api.delete("/v1/age-groups/${setup.ageGroup.id}/invites/${invite.code}") { bearerAuth(owner) }.status)
        assertEquals(HttpStatusCode.NotFound, api.post("/v1/invites/${invite.code}/accept") { bearerAuth(api.signUp()) }.status)
    }
}
