package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.LoginUseCase
import com.ricardofrango.footballmanager.domain.usecase.RegisterUseCase
import com.ricardofrango.footballmanager.model.LoginRequest
import com.ricardofrango.footballmanager.model.RegisterRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val registerUseCase by inject<RegisterUseCase>()
    val loginUseCase by inject<LoginUseCase>()

    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            registerUseCase(request)
                .onSuccess { success ->
                    if (success) {
                        call.respond(HttpStatusCode.Created, "User registered successfully")
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to register user")
                    }
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, error.message ?: "Registration failed")
                }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            loginUseCase(request)
                .onSuccess { response ->
                    call.respond(response)
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.Unauthorized, error.message ?: "Login failed")
                }
        }
    }
}
