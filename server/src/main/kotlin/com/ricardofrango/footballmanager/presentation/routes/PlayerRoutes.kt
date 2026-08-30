package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.AddPlayersToTeamUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreatePlayersUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetPlayerUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetPlayersUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdatePlayerUseCase
import com.ricardofrango.footballmanager.model.AddPlayersToTeamRequest
import com.ricardofrango.footballmanager.model.CreatePlayersRequest
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.model.UpdatePlayerRequest
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.playerRoutes() {
    val getPlayerUseCase by inject<GetPlayerUseCase>()
    val getPlayersUseCase by inject<GetPlayersUseCase>()
    val createPlayersUseCase by inject<CreatePlayersUseCase>()
    val addPlayersToTeamUseCase by inject<AddPlayersToTeamUseCase>()
    val updatePlayerUseCase by inject<UpdatePlayerUseCase>()

    authenticate("auth-jwt") {
        route("/players") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val teamId = call.request.queryParameters["teamId"]
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val players = getPlayersUseCase(currentUserId, clubId, teamId)
                call.respond(players)
            }

            post {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val playerRequest = call.receive<CreatePlayersRequest>()
                val player = createPlayersUseCase(currentUserId, playerRequest)
                call.respond(HttpStatusCode.Created, player)
            }

            post("/add-to-team") {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val teamPlayerRequest = call.receive<AddPlayersToTeamRequest>()
                val teamPlayer = addPlayersToTeamUseCase(currentUserId, teamPlayerRequest)
                call.respond(HttpStatusCode.Created, teamPlayer)
            }

            route("/{playerId}") {
                get {
                    val playerId =
                        call.parameters["playerId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val player = getPlayerUseCase(currentUserId, playerId)
                    call.respond(player)
                }

                patch {
                    val playerId = call.parameters["playerId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest
                    )
                    val currentUserId =
                        call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<UpdatePlayerRequest>()

                    val success = updatePlayerUseCase(currentUserId, playerId, request)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to update player")
                        )
                    }
                }
            }
        }
    }
}
