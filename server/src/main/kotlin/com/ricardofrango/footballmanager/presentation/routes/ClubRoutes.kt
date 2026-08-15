package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.CheckClubPermissionUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreateClubUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetClubsUseCase
import com.ricardofrango.footballmanager.model.Club
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.clubRoutes() {
    val getClubsUseCase by inject<GetClubsUseCase>()
    val createClubUseCase by inject<CreateClubUseCase>()
    val checkClubPermissionUseCase by inject<CheckClubPermissionUseCase>()

    authenticate("auth-jwt") {
        route("/clubs") {
            get {
                // For now, anyone authenticated can list clubs (MVP simplification)
                val clubs = getClubsUseCase()
                call.respond(clubs)
            }

            post {
                // For creating a club, we might need a Platform Admin role, 
                // but let's assume any registered user can create a club for now 
                // as per the MVP phase 1.
                val clubRequest = call.receive<Club>()
                createClubUseCase(clubRequest)
                    .onSuccess { success ->
                        if (success) {
                            call.respond(HttpStatusCode.Created)
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, "Failed to create club")
                        }
                    }
                    .onFailure { error ->
                        call.respond(HttpStatusCode.BadRequest, error.message ?: "Unknown error")
                    }
            }

            get("/{clubId}") {
                val clubId = call.parameters["clubId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)

                // Check if user has permission to see this club's details
                val hasPermission = checkClubPermissionUseCase(currentUserId, clubId, Role.entries)
                if (!hasPermission) {
                    return@get call.respond(HttpStatusCode.Forbidden, "Access denied")
                }
                
                // Logic to get specific club details
                call.respond(HttpStatusCode.OK, "Club details placeholder")
            }
        }
    }
}
