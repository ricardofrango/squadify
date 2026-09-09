package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.*
import com.ricardofrango.footballmanager.model.*
import com.ricardofrango.footballmanager.security.JwtConfig
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject

fun Route.matchRoutes() {
    val createMatchUseCase by inject<CreateMatchUseCase>()
    val getMatchesUseCase by inject<GetMatchesUseCase>()
    val getMatchDetailsUseCase by inject<GetMatchDetailsUseCase>()
    val updateMatchUseCase by inject<UpdateMatchUseCase>()
    val updateMatchStatusUseCase by inject<UpdateMatchStatusUseCase>()
    val manageCallUpsUseCase by inject<ManageCallUpsUseCase>()
    val updateCallUpStatusUseCase by inject<UpdateCallUpStatusUseCase>()
    val addMatchEventUseCase by inject<AddMatchEventUseCase>()
    val getMatchEventsUseCase by inject<GetMatchEventsUseCase>()
    val getMatchScoreUseCase by inject<GetMatchScoreUseCase>()
    val getLiveMatchUpdatesUseCase by inject<GetLiveMatchUpdatesUseCase>()

    authenticate("auth-jwt") {
        route("/matches") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val teamId = call.request.queryParameters["teamId"]
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val matches = getMatchesUseCase(currentUserId, clubId, teamId)
                call.respond(matches)
            }

            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<CreateMatchRequest>()
                val match = createMatchUseCase(currentUserId, request)
                call.respond(HttpStatusCode.Created, match)
            }

            route("/{matchId}") {
                get {
                    val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val matchId = call.parameters["matchId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val details = getMatchDetailsUseCase(currentUserId, matchId)
                    call.respond(details)
                }

                patch {
                    val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val matchId = call.parameters["matchId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val request = call.receive<UpdateMatchRequest>()

                    val success = updateMatchUseCase(currentUserId, matchId, request)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update match"))
                    }
                }

                patch("/status") {
                    val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val matchId = call.parameters["matchId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val request = call.receive<UpdateMatchStatusRequest>()

                    val success = updateMatchStatusUseCase(currentUserId, matchId, request.status)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update match status"))
                    }
                }

                route("/events") {
                    get {
                        val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val matchId = call.parameters["matchId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val events = getMatchEventsUseCase(currentUserId, matchId)
                        call.respond(events)
                    }

                    get("/score") {
                        val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val matchId = call.parameters["matchId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val score = getMatchScoreUseCase(currentUserId, matchId)
                        call.respond(score)
                    }

                    post {
                        val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        val matchId = call.parameters["matchId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val request = call.receive<CreateMatchEventRequest>()

                        val event = addMatchEventUseCase(currentUserId, matchId, request)
                        call.respond(HttpStatusCode.Created, event)
                    }
                }

                route("/call-up") {
                    post {
                        val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        val matchId = call.parameters["matchId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val request = call.receive<ManageCallUpRequest>()

                        val success = manageCallUpsUseCase(currentUserId, matchId, request)
                        if (success) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to manage call-ups"))
                        }
                    }
                }
            }

            route("/call-ups/{callUpId}") {
                patch("/status") {
                    val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val callUpId = call.parameters["callUpId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val request = call.receive<UpdateCallUpRequest>()

                    val success = updateCallUpStatusUseCase(currentUserId, callUpId, request)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update call-up status"))
                    }
                }
            }
        }
    }

    // 2. WebSocket Route with specific Token handling (outside of main authenticate block)
    route("/matches/{matchId}/live") {
        webSocket {
            val matchId = call.parameters["matchId"] ?: return@webSocket

            // Browser WebSockets do not support custom headers like Authorization.
            // We use a query parameter 'token' for authentication.
            try {
                val currentUserId = call.request.queryParameters["token"]
                    ?.run { JwtConfig.verifier.verify(this).subject }
                    ?: run {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                        return@webSocket
                    }

                getLiveMatchUpdatesUseCase(currentUserId, matchId)
                    .collect(::sendSerialized)
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Forbidden or Not Found"))
            }
        }
    }
}
