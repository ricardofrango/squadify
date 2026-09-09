package com.ricardofrango.clubmanager.presentation.routes

import com.ricardofrango.clubmanager.domain.usecase.AddClubMembersUseCase
import com.ricardofrango.clubmanager.domain.usecase.GetClubDetailsUseCase
import com.ricardofrango.clubmanager.domain.usecase.GetClubHomeUseCase
import com.ricardofrango.clubmanager.domain.usecase.GetClubsUseCase
import com.ricardofrango.clubmanager.domain.usecase.ProvisionClubUseCase
import com.ricardofrango.clubmanager.domain.usecase.UpdateClubUseCase
import com.ricardofrango.clubmanager.model.AddClubMembersRequest
import com.ricardofrango.clubmanager.model.ErrorResponse
import com.ricardofrango.clubmanager.model.ProvisionClubRequest
import com.ricardofrango.clubmanager.model.UpdateClubRequest
import com.ricardofrango.clubmanager.security.userId
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

fun Route.clubRoutes() {
    val getClubsUseCase by inject<GetClubsUseCase>()
    val getClubDetailsUseCase by inject<GetClubDetailsUseCase>()
    val getClubHomeUseCase by inject<GetClubHomeUseCase>()
    val provisionClubUseCase by inject<ProvisionClubUseCase>()
    val addClubMembersUseCase by inject<AddClubMembersUseCase>()
    val updateClubUseCase by inject<UpdateClubUseCase>()

    authenticate("auth-jwt") {
        route("/clubs") {
            get {
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val clubs = getClubsUseCase(currentUserId)
                call.respond(clubs)
            }

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

                get("/home") {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    call.respond(getClubHomeUseCase(currentUserId, clubId))
                }

                patch {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<UpdateClubRequest>()

                    val success = updateClubUseCase(currentUserId, clubId, request)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to update club")
                        )
                    }
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
