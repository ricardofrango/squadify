package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.CreateOpponentUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetOpponentsUseCase
import com.ricardofrango.footballmanager.model.CreateOpponentRequest
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.opponentRoutes() {
    val createOpponentUseCase by inject<CreateOpponentUseCase>()
    val getOpponentsUseCase by inject<GetOpponentsUseCase>()

    authenticate("auth-jwt") {
        route("/opponents") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val opponents = getOpponentsUseCase(currentUserId, clubId)
                call.respond(opponents)
            }
        }
        route("/opponent") {
            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val clubId = call.request.queryParameters["clubId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val request = call.receive<CreateOpponentRequest>()
                val opponent = createOpponentUseCase(currentUserId, clubId, request)
                call.respond(HttpStatusCode.Created, opponent)
            }
        }
    }
}
