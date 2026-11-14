package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.model.AgeGroupItemRequest
import com.ricardofrango.squadify.model.LeaveSpaceRequest
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SpaceSetup
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.model.StaffInvite
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Leaving a space (REQUIREMENTS §5c): the owner can't; a staff member leaves after their password, and loses
// the space and its age groups. Their account stays.
class CoachSpaceLeaveTest {

    @Test
    fun ownerCannotLeaveAStaffMemberCanAfterThePassword() = testApplication {
        application { module() }
        val api = jsonClient()

        val owner = api.signUp()
        val setup = api.post("/v1/space") {
            json(owner, SetUpSpaceRequest("Leave space", SportType.FOOTBALL, AgeGroupItemRequest("Sub-12", 2015, 2015, teamSize = 4), "Sub-12 A"))
        }.body<SpaceSetup>()
        val spaceId = setup.space.id
        // Staff need a plan with room for them (Free is just the owner).
        api.setPlan(spaceId, SpacePlan("PRO"))
        val staff = api.signUp()
        val invite = api.post("/v1/age-groups/${setup.ageGroup.id}/invites") { bearerAuth(owner) }.body<StaffInvite>()
        assertEquals(HttpStatusCode.OK, api.post("/v1/invites/${invite.code}/accept") { bearerAuth(staff) }.status)

        assertEquals(HttpStatusCode.Forbidden, api.post("/v1/spaces/$spaceId/leave") { json(owner, LeaveSpaceRequest("password1")) }.status)
        assertEquals(HttpStatusCode.BadRequest, api.post("/v1/spaces/$spaceId/leave") { json(staff, LeaveSpaceRequest("wrong-one")) }.status)

        assertEquals(HttpStatusCode.NoContent, api.post("/v1/spaces/$spaceId/leave") { json(staff, LeaveSpaceRequest("password1")) }.status)
        val spaces = api.get("/v1/spaces") { bearerAuth(staff) }.body<List<SpaceAgeGroups>>()
        assertTrue(spaces.none { it.space.id == spaceId }, "the space is still listed after leaving")
        assertEquals(HttpStatusCode.OK, api.get("/v1/spaces") { bearerAuth(staff) }.status)
    }
}
