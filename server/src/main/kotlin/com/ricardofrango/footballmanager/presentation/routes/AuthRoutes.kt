package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.LoginUseCase
import com.ricardofrango.footballmanager.domain.usecase.RefreshTokenUseCase
import com.ricardofrango.footballmanager.domain.usecase.RegisterUseCase
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.model.LoginRequest
import com.ricardofrango.footballmanager.model.RefreshTokenRequest
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
    val refreshTokenUseCase by inject<RefreshTokenUseCase>()

    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val success = registerUseCase(request)
            if (success) {
                call.respond(HttpStatusCode.Created, mapOf("message" to "User registered successfully"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to register user", "-1"))
            }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = loginUseCase(request)
            call.respond(response)
        }

        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()
            val response = refreshTokenUseCase(request.refreshToken)
            call.respond(response)
        }
    }
}
