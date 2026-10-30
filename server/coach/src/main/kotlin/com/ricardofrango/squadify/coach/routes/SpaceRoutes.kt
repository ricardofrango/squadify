package com.ricardofrango.squadify.coach.routes

import com.ricardofrango.squadify.coach.domain.GetSpaceUseCase
import com.ricardofrango.squadify.coach.domain.GetSpacesUseCase
import com.ricardofrango.squadify.coach.domain.SetUpSpaceUseCase
import com.ricardofrango.squadify.model.SetUpSpaceRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * The coach's spaces: `/spaces` lists every one they're in with their age groups there; `/space` creates
 * their own (the first setup, with its first age group and team), and still returns it (404 before),
 * until the app moves to `/spaces`.
 */
fun Route.spaceRoutes() {
    val getSpaceUseCase by inject<GetSpaceUseCase>()
    val setUpSpaceUseCase by inject<SetUpSpaceUseCase>()
    val getSpacesUseCase by inject<GetSpacesUseCase>()

    authenticate("auth-jwt") {
        get("/spaces") {
            val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getSpacesUseCase(currentUserId))
        }
        route("/space") {
            get {
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getSpaceUseCase(currentUserId))
            }
            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                call.respond(HttpStatusCode.Created, setUpSpaceUseCase(currentUserId, call.receive<SetUpSpaceRequest>()))
            }
        }
    }
}
