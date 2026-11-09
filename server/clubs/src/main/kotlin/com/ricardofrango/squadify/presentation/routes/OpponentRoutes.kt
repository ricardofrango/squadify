package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.*
import com.ricardofrango.squadify.model.*
import com.ricardofrango.squadify.security.userId
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
    val getOpponentListUseCase by inject<GetOpponentListUseCase>()
    val getOpponentDetailsUseCase by inject<GetOpponentDetailsUseCase>()
    val deleteOpponentUseCase by inject<DeleteOpponentUseCase>()

    authenticate("auth-jwt") {
        route("/opponents") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val ageGroupId = call.request.queryParameters["ageGroupId"]
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val opponents = getOpponentsUseCase(currentUserId, clubId, ageGroupId)
                call.respond(opponents)
            }

            // The Opponents screen: the list and whether the caller can create. With `limit`, a page of it
            // (`offset`), searched on `q`; without, the whole list.
            get("/list") {
                val params = call.request.queryParameters
                val clubId = params["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val ageGroupId = params["ageGroupId"]
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(
                    getOpponentListUseCase(
                        currentUserId, clubId, ageGroupId,
                        search = params["q"],
                        limit = Paging.limitOf(params["limit"]),
                        offset = Paging.offsetOf(params["offset"])
                    )
                )
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

            // The opponent and every game against it, for good. 204.
            delete("/{opponentId}") {
                val opponentId = call.parameters["opponentId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                deleteOpponentUseCase(currentUserId, opponentId)
                call.respond(HttpStatusCode.NoContent)
            }

            // Opponent details: the opponent and every match against them.
            get("/{opponentId}") {
                val opponentId = call.parameters["opponentId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getOpponentDetailsUseCase(currentUserId, opponentId))
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
