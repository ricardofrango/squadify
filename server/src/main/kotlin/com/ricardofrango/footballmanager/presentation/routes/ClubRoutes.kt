package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.CheckClubPermissionUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreateClubUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetClubDetailsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetClubsUseCase
import com.ricardofrango.footballmanager.domain.usecase.ProvisionClubUseCase
import com.ricardofrango.footballmanager.model.*
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.clubRoutes() {
    val getClubsUseCase by inject<GetClubsUseCase>()
    val getClubDetailsUseCase by inject<GetClubDetailsUseCase>()
    val provisionClubUseCase by inject<ProvisionClubUseCase>()

    authenticate("auth-jwt") {
        route("/clubs") {
            get {
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val clubs = getClubsUseCase(currentUserId)
                call.respond(clubs)
            }

            post("/provision") {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<ProvisionClubRequest>()
                
                val club = provisionClubUseCase(currentUserId, request.directorId, request.clubName)
                call.respond(HttpStatusCode.Created, club)
            }

            get("/{clubId}") {
                val clubId = call.parameters["clubId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val club = getClubDetailsUseCase(currentUserId, clubId)
                call.respond(club)
            }
        }
    }
}
