package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.CreateClubUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetClubsUseCase
import com.ricardofrango.footballmanager.model.Club
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.clubRoutes() {
    val getClubsUseCase by inject<GetClubsUseCase>()
    val createClubUseCase by inject<CreateClubUseCase>()

    route("/clubs") {
        get {
            val clubs = getClubsUseCase()
            call.respond(clubs)
        }

        post {
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
    }
}
