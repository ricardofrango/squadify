package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.*
import com.ricardofrango.squadify.model.*
import com.ricardofrango.squadify.security.JwtTokens
import com.ricardofrango.squadify.security.userId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject

/**
 * The game routes both servers serve, at the same paths with the same requests and responses
 * (TECHNICAL_DECISIONS §72): matches, call-ups, the live game and its WebSockets, the record and
 * report, the season records, full stats and a player's season stats. Each server mounts them with
 * its own [tokens], which the WebSockets check (browser WebSockets can't send an Authorization
 * header, so the token comes as a query parameter); everything else is behind `auth-jwt`.
 */
fun Route.gameRoutes(tokens: JwtTokens) {
    val createMatchUseCase by inject<CreateMatchUseCase>()
    val getMatchesUseCase by inject<GetMatchesUseCase>()
    val getMatchDetailsUseCase by inject<GetMatchDetailsUseCase>()
    val updateMatchUseCase by inject<UpdateMatchUseCase>()
    val deleteMatchUseCase by inject<DeleteMatchUseCase>()
    val updateMatchStatusUseCase by inject<UpdateMatchStatusUseCase>()
    val manageCallUpsUseCase by inject<ManageCallUpsUseCase>()
    val addMatchEventUseCase by inject<AddMatchEventUseCase>()
    val deleteMatchEventUseCase by inject<DeleteMatchEventUseCase>()
    val saveMatchRecordUseCase by inject<SaveMatchRecordUseCase>()
    val getMatchEventsUseCase by inject<GetMatchEventsUseCase>()
    val getMatchScoreUseCase by inject<GetMatchScoreUseCase>()
    val getLiveMatchUpdatesUseCase by inject<GetLiveMatchUpdatesUseCase>()
    val startMatchUseCase by inject<StartMatchUseCase>()
    val setMatchLineupUseCase by inject<SetMatchLineupUseCase>()
    val pauseMatchUseCase by inject<PauseMatchUseCase>()
    val resumeMatchUseCase by inject<ResumeMatchUseCase>()
    val setMatchMinuteUseCase by inject<SetMatchMinuteUseCase>()
    val getMatchReportUseCase by inject<GetMatchReportUseCase>()
    val saveMatchReportUseCase by inject<SaveMatchReportUseCase>()
    val getClubMatchesUpdatesUseCase by inject<GetClubMatchesUpdatesUseCase>()
    val getTeamSeasonRecordUseCase by inject<GetTeamSeasonRecordUseCase>()
    val getAgeGroupSeasonRecordUseCase by inject<GetAgeGroupSeasonRecordUseCase>()
    val getFullStatsUseCase by inject<GetFullStatsUseCase>()
    val getPlayerSeasonStatsUseCase by inject<GetPlayerSeasonStatsUseCase>()

    authenticate("auth-jwt") {
        route("/matches") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val teamId = call.request.queryParameters["teamId"]
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val params = call.request.queryParameters
                // With `limit`: one page of the club Matches tab, filtered here. Without it: the whole
                // list, for app builds from before paging.
                val limit = Paging.limitOf(params["limit"])
                if (limit != null) {
                    val status = params["status"]?.let { s -> MatchListStatus.entries.firstOrNull { it.name == s } }
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("status must be NOT_PLAYED, UPCOMING or FINISHED", "VAL_001"))
                    return@get call.respond(
                        getMatchesUseCase.page(
                            currentUserId, clubId, status,
                            seasonId = params["seasonId"],
                            ageGroupId = params["ageGroupId"],
                            teamId = teamId,
                            limit = limit,
                            offset = Paging.offsetOf(params["offset"])
                        )
                    )
                }
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

                delete {
                    val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    val matchId = call.parameters["matchId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)

                    val success = deleteMatchUseCase(currentUserId, matchId)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete match"))
                    }
                }

                // The coach's post-match report and per-player ratings -- FINISHED matches only.
                route("/report") {
                    get {
                        val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                        val matchId = call.parameters["matchId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        call.respond(getMatchReportUseCase(currentUserId, matchId))
                    }
                    put {
                        val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                        val matchId = call.parameters["matchId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                        val request = call.receive<SaveMatchReportRequest>()
                        call.respond(saveMatchReportUseCase(currentUserId, matchId, request))
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

                post("/start") {
                    val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val matchId = call.parameters["matchId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val request = call.receive<StartMatchRequest>()

                    val success = startMatchUseCase(currentUserId, matchId, request)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to start match"))
                    }
                }

                // The live pitch view: the formation and who stands in each position (see Formation.kt).
                put("/lineup") {
                    val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val matchId = call.parameters["matchId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val request = call.receive<SetMatchLineupRequest>()
                    call.respond(setMatchLineupUseCase(currentUserId, matchId, request))
                }

                post("/pause") {
                    val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val matchId = call.parameters["matchId"] ?: return@post call.respond(HttpStatusCode.BadRequest)

                    val success = pauseMatchUseCase(currentUserId, matchId)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to pause match"))
                    }
                }

                post("/resume") {
                    val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val matchId = call.parameters["matchId"] ?: return@post call.respond(HttpStatusCode.BadRequest)

                    val success = resumeMatchUseCase(currentUserId, matchId)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to resume match"))
                    }
                }

                patch("/minute") {
                    val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val matchId = call.parameters["matchId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val request = call.receive<SetMatchMinuteRequest>()

                    val success = setMatchMinuteUseCase(currentUserId, matchId, request.minute)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to set match minute"))
                    }
                }

                // After the game: correct its goals and cards, or enter a game not followed live.
                put("/record") {
                    val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    val matchId = call.parameters["matchId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val request = call.receive<SaveMatchRecordRequest>()
                    call.respond(saveMatchRecordUseCase(currentUserId, matchId, request))
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

                    delete("/{eventId}") {
                        val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                        val matchId = call.parameters["matchId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                        val eventId = call.parameters["eventId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)

                        val success = deleteMatchEventUseCase(currentUserId, matchId, eventId)
                        if (success) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete match event"))
                        }
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
        }

        // Finished matches + players' stats lines for the season record and leaders (design 31).
        get("/teams/{teamId}/season-record") {
            val teamId = call.parameters["teamId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getTeamSeasonRecordUseCase(currentUserId, teamId))
        }

        // Every player's stats for the Full stats table: only those who played for this team.
        get("/teams/{teamId}/full-stats") {
            val teamId = call.parameters["teamId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getFullStatsUseCase.team(currentUserId, teamId))
        }

        // Every team's season record + the pool, for the Stats and Members tabs (design 33).
        get("/age-groups/{ageGroupId}/season-record") {
            val ageGroupId = call.parameters["ageGroupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getAgeGroupSeasonRecordUseCase(currentUserId, ageGroupId))
        }

        // Every pool player's stats for the Full stats table.
        get("/age-groups/{ageGroupId}/full-stats") {
            val ageGroupId = call.parameters["ageGroupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getFullStatsUseCase.ageGroup(currentUserId, ageGroupId))
        }

        // Every season's per-match stats lines (design 30); totals are derived client-side.
        get("/players/{playerId}/season-stats") {
            val playerId = call.parameters["playerId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(getPlayerSeasonStatsUseCase(currentUserId, playerId))
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
                    ?.run { tokens.verifier.verify(this).subject }
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

    // The club-wide Matches tab's live updates (§28), with the same token-query-param auth.
    route("/clubs/{clubId}/matches/live") {
        webSocket {
            val clubId = call.parameters["clubId"] ?: return@webSocket

            try {
                val currentUserId = call.request.queryParameters["token"]
                    ?.run { tokens.verifier.verify(this).subject }
                    ?: run {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                        return@webSocket
                    }

                getClubMatchesUpdatesUseCase(currentUserId, clubId)
                    .collect(::sendSerialized)
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Forbidden or Not Found"))
            }
        }
    }
}
