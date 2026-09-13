package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.CreateTeamUseCase
import com.ricardofrango.squadify.domain.usecase.CreateTeamsUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamPlayersUseCase
import com.ricardofrango.squadify.domain.usecase.GetTeamsUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateTeamUseCase
import com.ricardofrango.squadify.model.CreateTeamRequest
import com.ricardofrango.squadify.model.CreateTeamsRequest
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.UpdateTeamRequest
import com.ricardofrango.squadify.security.userId
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
    val createTeamsUseCase by inject<CreateTeamsUseCase>()
    val getTeamsUseCase by inject<GetTeamsUseCase>()
    val getTeamPlayersUseCase by inject<GetTeamPlayersUseCase>()
    val getTeamDetailsUseCase by inject<GetTeamDetailsUseCase>()
    val updateTeamUseCase by inject<UpdateTeamUseCase>()

    authenticate("auth-jwt") {
        route("/teams") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val teams = getTeamsUseCase(currentUserId, clubId)
                call.respond(teams)
            }

            post {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val teamRequest = call.receive<CreateTeamRequest>()

                val team = createTeamUseCase(currentUserId, teamRequest)
                call.respond(HttpStatusCode.Created, team)
            }

            post("/batch") {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<CreateTeamsRequest>()

                val teams = createTeamsUseCase(currentUserId, request)
                call.respond(HttpStatusCode.Created, teams)
            }

            route("/{teamId}") {
                // A team's players are its age group's pool (players belong to age groups now);
                // staff is served from /age-groups/{ageGroupId}/staff.
                get("/players") {
                    val teamId = call.parameters["teamId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val players = getTeamPlayersUseCase(currentUserId, teamId)
                    call.respond(players)
                }

                get("/details") {
                    val teamId = call.parameters["teamId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val details = getTeamDetailsUseCase(currentUserId, teamId)
                    call.respond(details)
                }

                patch {
                    val teamId = call.parameters["teamId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest
                    )
                    val currentUserId =
                        call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<UpdateTeamRequest>()

                    val success = updateTeamUseCase(currentUserId, teamId, request)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to update team")
                        )
                    }
                }
            }
        }
    }
}
