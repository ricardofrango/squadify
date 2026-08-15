package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.CreateTeamUseCase
import com.ricardofrango.footballmanager.domain.usecase.ManageTeamUseCase
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.teamRoutes() {
    val createTeamUseCase by inject<CreateTeamUseCase>()
    val manageTeamUseCase by inject<ManageTeamUseCase>()

    authenticate("auth-jwt") {
        route("/teams") {
            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val teamRequest = call.receive<Team>()
                
                createTeamUseCase(currentUserId, teamRequest)
                    .onSuccess { success ->
                        if (success) call.respond(HttpStatusCode.Created)
                        else call.respond(HttpStatusCode.InternalServerError)
                    }
                    .onFailure { error ->
                        when(error) {
                            is IllegalAccessException -> call.respond(HttpStatusCode.Forbidden, error.message!!)
                            else -> call.respond(HttpStatusCode.BadRequest, error.message ?: "Unknown error")
                        }
                    }
            }

            patch("/{teamId}") {
                val teamId = call.parameters["teamId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                
                // For simplicity, we get clubId from the request body or we could fetch team first.
                // In a real app, we'd fetch the team to know its clubId.
                // Let's assume we have a way to check if user can manage this specific team.
                
                // Logic to update team...
                call.respond(HttpStatusCode.OK, "Manage team logic here")
            }
        }
    }
}
