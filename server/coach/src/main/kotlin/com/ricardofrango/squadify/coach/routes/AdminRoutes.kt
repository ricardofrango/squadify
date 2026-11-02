package com.ricardofrango.squadify.coach.routes

import com.ricardofrango.squadify.coach.domain.GetAdminSpacesUseCase
import com.ricardofrango.squadify.coach.domain.SetSpacePlanUseCase
import com.ricardofrango.squadify.model.SetSpacePlanRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/** The platform admin's tools (REQUIREMENTS §5c): the spaces and their plans, until payments. */
fun Route.adminRoutes() {
    val getSpaces by inject<GetAdminSpacesUseCase>()
    val setPlan by inject<SetSpacePlanUseCase>()

    authenticate("auth-jwt") {
        route("/admin/spaces") {
            get {
                val userId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getSpaces(userId))
            }
            put("/{spaceId}/plan") {
                val spaceId = call.parameters["spaceId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val userId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                setPlan(userId, spaceId, call.receive<SetSpacePlanRequest>().plan)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
