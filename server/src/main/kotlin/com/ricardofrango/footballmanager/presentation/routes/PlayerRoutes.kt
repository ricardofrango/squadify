package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.AddPlayerToTeamUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreatePlayerUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetPlayersUseCase
import com.ricardofrango.footballmanager.model.AddPlayerToTeamRequest
import com.ricardofrango.footballmanager.model.CreatePlayerRequest
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

fun Route.playerRoutes() {
    val getPlayersUseCase by inject<GetPlayersUseCase>()
    val createPlayerUseCase by inject<CreatePlayerUseCase>()
    val addPlayerToTeamUseCase by inject<AddPlayerToTeamUseCase>()

    authenticate("auth-jwt") {
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
        }
    }
}
