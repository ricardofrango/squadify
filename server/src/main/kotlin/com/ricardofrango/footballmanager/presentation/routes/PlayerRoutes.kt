package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.*
import com.ricardofrango.footballmanager.model.*
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.playerRoutes() {
    val getPlayerUseCase by inject<GetPlayerUseCase>()
    val getPlayersUseCase by inject<GetPlayersUseCase>()
    val createPlayerUseCase by inject<CreatePlayerUseCase>()
    val createPlayersUseCase by inject<CreatePlayersUseCase>()
    val addPlayerToTeamUseCase by inject<AddPlayerToTeamUseCase>()
    val addPlayersToTeamUseCase by inject<AddPlayersToTeamUseCase>()
    val updatePlayerStatusUseCase by inject<UpdatePlayerStatusUseCase>()

    authenticate("auth-jwt") {
        route("/player") {
            get {
                val playerId = call.request.queryParameters["playerId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("playerId is required", "VAL_001")
                )
                val player = getPlayerUseCase(playerId)
                call.respond(player)
            }

            post {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val playerRequest = call.receive<CreatePlayerRequest>()
                val player = createPlayerUseCase(currentUserId, playerRequest)
                call.respond(HttpStatusCode.Created, player)
            }

            post("/add-to-team") {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val clubId = call.request.queryParameters["clubId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val teamPlayerRequest = call.receive<AddPlayerToTeamRequest>()
                val teamPlayer = addPlayerToTeamUseCase(currentUserId, clubId, teamPlayerRequest)
                call.respond(HttpStatusCode.Created, teamPlayer)
            }

            patch("/{playerId}/status") {
                val playerId = call.parameters["playerId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val clubId = call.request.queryParameters["clubId"] ?: return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<UpdateActiveStatusRequest>()

                val success = updatePlayerStatusUseCase(currentUserId, clubId, playerId, request.active)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update player status"))
                }
            }
        }

        route("/players") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val teamId = call.request.queryParameters["teamId"]
                val players = getPlayersUseCase(clubId, teamId)
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
                val clubId = call.request.queryParameters["clubId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val teamPlayerRequest = call.receive<AddPlayersToTeamRequest>()
                val teamPlayer = addPlayersToTeamUseCase(currentUserId, clubId, teamPlayerRequest)
                call.respond(HttpStatusCode.Created, teamPlayer)
            }
        }
    }
}
