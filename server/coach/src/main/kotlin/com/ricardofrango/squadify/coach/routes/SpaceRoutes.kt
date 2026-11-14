package com.ricardofrango.squadify.coach.routes

import com.ricardofrango.squadify.presentation.routes.appLanguage
import com.ricardofrango.squadify.coach.domain.AddAgeGroupsUseCase
import com.ricardofrango.squadify.coach.domain.DeleteSpaceUseCase
import com.ricardofrango.squadify.coach.domain.LeaveSpaceUseCase
import com.ricardofrango.squadify.coach.domain.GetSpaceDetailsUseCase
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.coach.domain.GetPlansUseCase
import com.ricardofrango.squadify.coach.domain.RequestPlanUseCase
import com.ricardofrango.squadify.coach.domain.SetUpSpaceUseCase
import com.ricardofrango.squadify.model.AddAgeGroupsRequest
import com.ricardofrango.squadify.model.DeleteSpaceRequest
import com.ricardofrango.squadify.model.LeaveSpaceRequest
import com.ricardofrango.squadify.model.RequestPlanRequest
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject

/**
 * The coach's spaces: `/spaces` lists every one they're in with their age groups there, `/spaces/{id}`
 * one of them for its details screen (its age groups' teams and next games); `POST /space`
 * sets up their own, with its first age group and team. `/plans?language=`: every plan with what it
 * has, for the app to show, and the coach's own plan; `POST /plans/request?language=`: the owner
 * asks for another, until payments (set at once on a local server), and gets the plans back.
 */
fun Route.spaceRoutes() {
    val setUpSpaceUseCase by inject<SetUpSpaceUseCase>()
    val getSpacesUseCase by inject<GetSpacesUseCase>()
    val getSpaceDetailsUseCase by inject<GetSpaceDetailsUseCase>()
    val addAgeGroupsUseCase by inject<AddAgeGroupsUseCase>()
    val getPlansUseCase by inject<GetPlansUseCase>()
    val requestPlanUseCase by inject<RequestPlanUseCase>()
    val deleteSpaceUseCase by inject<DeleteSpaceUseCase>()
    val leaveSpaceUseCase by inject<LeaveSpaceUseCase>()

    authenticate("auth-jwt") {
        get("/spaces") {
            val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getSpacesUseCase(currentUserId))
        }
        get("/spaces/{spaceId}") {
            val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val spaceId = call.parameters["spaceId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.respond(getSpaceDetailsUseCase(currentUserId, spaceId))
        }
        get("/plans") {
            val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getPlansUseCase(currentUserId, call.appLanguage()))
        }
        post("/plans/request") {
            val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(requestPlanUseCase(currentUserId, call.receive<RequestPlanRequest>().plan, call.appLanguage()))
        }
        // The age-group wizard: age groups for one club, with their teams, in their own space (made the first time).
        post("/space/age-groups") {
            val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(HttpStatusCode.Created, addAgeGroupsUseCase(currentUserId, call.receive<AddAgeGroupsRequest>()))
        }
        // The owner deletes the space, with everything in it, after their password (not the account).
        post("/spaces/{spaceId}/delete") {
            val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val spaceId = call.parameters["spaceId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            deleteSpaceUseCase(currentUserId, spaceId, call.receive<DeleteSpaceRequest>())
            call.respond(HttpStatusCode.NoContent)
        }
        // A staff member leaves the space, after their password (the owner can't: they delete it).
        post("/spaces/{spaceId}/leave") {
            val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val spaceId = call.parameters["spaceId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            leaveSpaceUseCase(currentUserId, spaceId, call.receive<LeaveSpaceRequest>().currentPassword)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/space") {
            val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respond(HttpStatusCode.Created, setUpSpaceUseCase(currentUserId, call.receive<SetUpSpaceRequest>()))
        }
    }
}
