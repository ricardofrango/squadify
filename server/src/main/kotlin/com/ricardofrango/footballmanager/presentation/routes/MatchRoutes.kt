package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.*
import com.ricardofrango.footballmanager.model.*
import com.ricardofrango.footballmanager.security.MatchSessionManager
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.channels.consumeEach
import org.koin.ktor.ext.inject

fun Route.matchRoutes() {
    val createMatchUseCase by inject<CreateMatchUseCase>()
    val getMatchesUseCase by inject<GetMatchesUseCase>()
    val getMatchDetailsUseCase by inject<GetMatchDetailsUseCase>()
    val updateMatchStatusUseCase by inject<UpdateMatchStatusUseCase>()
    val manageCallUpUseCase by inject<ManageCallUpUseCase>()
    val addMatchEventUseCase by inject<AddMatchEventUseCase>()
    val getMatchEventsUseCase by inject<GetMatchEventsUseCase>()

    authenticate("auth-jwt") {
        route("/matches") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val teamId = call.request.queryParameters["teamId"]
                val matches = getMatchesUseCase(clubId, teamId)
                call.respond(matches)
            }
        }
        route("/match") {
            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val clubId = call.request.queryParameters["clubId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val request = call.receive<CreateMatchRequest>()
                val match = createMatchUseCase(currentUserId, clubId, request)
                call.respond(HttpStatusCode.Created, match)
            }

            route("/{matchId}") {
                get("/details") {
                    val matchId = call.parameters["matchId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val details = getMatchDetailsUseCase(matchId)
                    call.respond(details)
                }

                patch("/status") {
                    val matchId = call.parameters["matchId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val clubId = call.request.queryParameters["clubId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("clubId is required", "VAL_001")
                    )
                    val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<UpdateMatchStatusRequest>()

                    val success = updateMatchStatusUseCase(currentUserId, clubId, matchId, request.status)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update match status"))
                    }
                }

                route("/events") {
                    get {
                        val matchId = call.parameters["matchId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val events = getMatchEventsUseCase.getEvents(matchId)
                        call.respond(events)
                    }

                    get("/score") {
                        val matchId = call.parameters["matchId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val score = getMatchEventsUseCase.getScore(matchId)
                        call.respond(score)
                    }

                    post {
                        val matchId = call.parameters["matchId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        val request = call.receive<CreateMatchEventRequest>()

                        val event = addMatchEventUseCase(currentUserId, matchId, request)
                        call.respond(HttpStatusCode.Created, event)
                    }
                }

                webSocket("/live") {
                    val matchId = call.parameters["matchId"] ?: return@webSocket
                    MatchSessionManager.addSession(matchId, this)
                    try {
                        incoming.consumeEach { }
                    } finally {
                        MatchSessionManager.removeSession(matchId, this)
                    }
                }

                route("/call-up") {
                    post {
                        val matchId = call.parameters["matchId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val clubId = call.request.queryParameters["clubId"] ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("clubId is required", "VAL_001")
                        )
                        val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                        val request = call.receive<ManageCallUpRequest>()

                        val success = manageCallUpUseCase.manageCallUps(currentUserId, clubId, matchId, request.playerIds)
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
                    val callUpId = call.parameters["callUpId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val clubId = call.request.queryParameters["clubId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("clubId is required", "VAL_001")
                    )
                    val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<UpdateCallUpRequest>()

                    val success = manageCallUpUseCase.updateCallUpStatus(currentUserId, clubId, callUpId, request.status, request.notes)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update call-up status"))
                    }
                }
            }
        }
    }
}
