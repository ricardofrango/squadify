package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.CreateTeamUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetTeamsUseCase
import com.ricardofrango.footballmanager.domain.usecase.ManageTeamUseCase
import com.ricardofrango.footballmanager.model.CreateTeamRequest
import com.ricardofrango.footballmanager.model.ErrorResponse
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

fun Route.teamRoutes() {
    val createTeamUseCase by inject<CreateTeamUseCase>()
    val manageTeamUseCase by inject<ManageTeamUseCase>()
    val getTeamsUseCase by inject<GetTeamsUseCase>()

    authenticate("auth-jwt") {
        route("/teams") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val teams = getTeamsUseCase(clubId)
                call.respond(teams)
            }

            post {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val teamRequest = call.receive<CreateTeamRequest>()

                val success = createTeamUseCase(currentUserId, teamRequest)
                if (success) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to create team", "-1")
                    )
                }
            }

            patch("/{teamId}") {
                val teamId = call.parameters["teamId"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val currentUserId =
                    call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)

                // For simplicity, we get clubId from the request body or we could fetch team first.
                // In a real app, we'd fetch the team to know its clubId.
                // Let's assume we have a way to check if user can manage this specific team.

                // Logic to update team...
                call.respond(HttpStatusCode.OK, "Manage team logic here")
            }
        }
    }
}
