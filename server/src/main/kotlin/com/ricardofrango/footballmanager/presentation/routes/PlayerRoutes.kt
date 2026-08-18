package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.AddPlayerToTeamUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreatePlayerUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetPlayersUseCase
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.TeamPlayer
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.playerRoutes() {
    val getPlayersUseCase by inject<GetPlayersUseCase>()
    val createPlayerUseCase by inject<CreatePlayerUseCase>()
    val addPlayerToTeamUseCase by inject<AddPlayerToTeamUseCase>()

    authenticate("auth-jwt") {
        route("/players") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("clubId is required", "VAL_001"))
                val teamId = call.request.queryParameters["teamId"]
                val players = getPlayersUseCase(clubId, teamId)
                call.respond(players)
            }

            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val playerRequest = call.receive<Player>()
                val success = createPlayerUseCase(currentUserId, playerRequest)
                if (success) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create player", "-1"))
                }
            }

            post("/add-to-team") {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val clubId = call.request.queryParameters["clubId"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("clubId is required", "VAL_001"))
                val teamPlayerRequest = call.receive<TeamPlayer>()
                val success = addPlayerToTeamUseCase(currentUserId, clubId, teamPlayerRequest)
                if (success) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to add player to team", "-1"))
                }
            }
        }
    }
}
