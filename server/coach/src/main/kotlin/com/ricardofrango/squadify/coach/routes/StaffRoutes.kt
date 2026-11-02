package com.ricardofrango.squadify.coach.routes

import com.ricardofrango.squadify.coach.domain.AcceptStaffInviteUseCase
import com.ricardofrango.squadify.coach.domain.CreateStaffInviteUseCase
import com.ricardofrango.squadify.coach.domain.GetStaffInvitesUseCase
import com.ricardofrango.squadify.coach.domain.RemoveStaffUseCase
import com.ricardofrango.squadify.coach.domain.RevokeStaffInviteUseCase
import com.ricardofrango.squadify.model.CreateStaffInviteRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Staff: the owner shares a code for an age group, whoever uses it joins as staff, and the owner can
 * withdraw a code or take someone off. The age group's staff list itself is in its details.
 */
fun Route.staffRoutes() {
    val createInvite by inject<CreateStaffInviteUseCase>()
    val getInvites by inject<GetStaffInvitesUseCase>()
    val revokeInvite by inject<RevokeStaffInviteUseCase>()
    val acceptInvite by inject<AcceptStaffInviteUseCase>()
    val removeStaff by inject<RemoveStaffUseCase>()

    authenticate("auth-jwt") {
        route("/age-groups/{ageGroupId}") {
            route("/invites") {
                get {
                    val ageGroupId = call.parameters["ageGroupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    call.respond(getInvites(userId, ageGroupId))
                }
                post {
                    val ageGroupId = call.parameters["ageGroupId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    // No body: a code to share by hand. With an email: also sent there.
                    val request = runCatching { call.receiveNullable<CreateStaffInviteRequest>() }.getOrNull() ?: CreateStaffInviteRequest()
                    call.respond(HttpStatusCode.Created, createInvite(userId, ageGroupId, request))
                }
                delete("/{code}") {
                    val ageGroupId = call.parameters["ageGroupId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val code = call.parameters["code"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val userId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    revokeInvite(userId, ageGroupId, code)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
            delete("/staff/{userId}") {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val staffUserId = call.parameters["userId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                removeStaff(userId, ageGroupId, staffUserId)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // Using a code: joins the space as staff on its age group, and returns the space.
        post("/invites/{code}/accept") {
            val code = call.parameters["code"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val userId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(acceptInvite(userId, code))
        }
    }
}
