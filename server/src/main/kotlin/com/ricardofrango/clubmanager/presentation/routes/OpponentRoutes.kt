package com.ricardofrango.clubmanager.presentation.routes

import com.ricardofrango.clubmanager.domain.usecase.*
import com.ricardofrango.clubmanager.model.*
import com.ricardofrango.clubmanager.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.opponentRoutes() {
    val createOpponentUseCase by inject<CreateOpponentUseCase>()
    val getOpponentsUseCase by inject<GetOpponentsUseCase>()
    val updateOpponentUseCase by inject<UpdateOpponentUseCase>()

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

            patch("/{opponentId}") {
                val opponentId = call.parameters["opponentId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<UpdateOpponentRequest>()

                val success = updateOpponentUseCase(currentUserId, opponentId, request)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update opponent"))
                }
            }
        }
    }
}
