package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.CreateSeasonUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetSeasonsUseCase
import com.ricardofrango.footballmanager.model.CreateSeasonRequest
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

fun Route.seasonRoutes() {
    val getSeasonsUseCase by inject<GetSeasonsUseCase>()
    val createSeasonUseCase by inject<CreateSeasonUseCase>()

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
                val success = createSeasonUseCase(currentUserId, seasonRequest)
                if (success) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to create season", "-1")
                    )
                }
            }
        }
    }
}
