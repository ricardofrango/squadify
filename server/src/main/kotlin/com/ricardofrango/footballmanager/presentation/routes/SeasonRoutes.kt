package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.CreateSeasonUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetSeasonsUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdateSeasonStatusUseCase
import com.ricardofrango.footballmanager.model.CreateSeasonRequest
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.model.UpdateActiveStatusRequest
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.seasonRoutes() {
    val getSeasonsUseCase by inject<GetSeasonsUseCase>()
    val createSeasonUseCase by inject<CreateSeasonUseCase>()
    val updateSeasonStatusUseCase by inject<UpdateSeasonStatusUseCase>()

    authenticate("auth-jwt") {
        route("/seasons") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val seasons = getSeasonsUseCase(clubId)
                call.respond(seasons)
            }

            post {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val seasonRequest = call.receive<CreateSeasonRequest>()
                val season = createSeasonUseCase(currentUserId, seasonRequest)
                call.respond(HttpStatusCode.Created, season)
            }
        }
        route("/season") {
            patch("/{seasonId}/status") {
                val seasonId = call.parameters["seasonId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val clubId = call.request.queryParameters["clubId"] ?: return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<UpdateActiveStatusRequest>()

                val success = updateSeasonStatusUseCase(currentUserId, clubId, seasonId, request.active)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update season status"))
                }
            }
        }
    }
}
