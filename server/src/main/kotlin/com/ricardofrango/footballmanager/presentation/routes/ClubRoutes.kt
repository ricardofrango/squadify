package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.AddClubMembersUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetClubDetailsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetClubsUseCase
import com.ricardofrango.footballmanager.domain.usecase.ProvisionClubUseCase
import com.ricardofrango.footballmanager.model.AddClubMembersRequest
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.model.ProvisionClubRequest
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

fun Route.clubRoutes() {
    val getClubsUseCase by inject<GetClubsUseCase>()
    val getClubDetailsUseCase by inject<GetClubDetailsUseCase>()
    val provisionClubUseCase by inject<ProvisionClubUseCase>()
    val addClubMembersUseCase by inject<AddClubMembersUseCase>()

    authenticate("auth-jwt") {
        route("/clubs") {
            get {
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val clubs = getClubsUseCase(currentUserId)
                call.respond(clubs)
            }
        }

        route("/club") {
            post("/provision") {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<ProvisionClubRequest>()

                val club = provisionClubUseCase(currentUserId, request)
                call.respond(HttpStatusCode.Created, club)
            }

            route("/{clubId}") {
                get {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val club = getClubDetailsUseCase(currentUserId, clubId)
                    call.respond(club)
                }

                post("/members") {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<AddClubMembersRequest>()

                    val success = addClubMembersUseCase(currentUserId, clubId, request)
                    if (success) {
                        call.respond(HttpStatusCode.Created)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to add club members")
                        )
                    }
                }
            }
        }
    }
}
