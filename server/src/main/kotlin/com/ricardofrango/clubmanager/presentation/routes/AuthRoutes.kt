package com.ricardofrango.clubmanager.presentation.routes

import com.ricardofrango.clubmanager.domain.usecase.LoginUseCase
import com.ricardofrango.clubmanager.domain.usecase.RefreshTokenUseCase
import com.ricardofrango.clubmanager.domain.usecase.RegisterUseCase
import com.ricardofrango.clubmanager.model.ErrorResponse
import com.ricardofrango.clubmanager.model.LoginRequest
import com.ricardofrango.clubmanager.model.RefreshTokenRequest
import com.ricardofrango.clubmanager.model.RegisterRequest
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
            val user = registerUseCase(request)
            call.respond(HttpStatusCode.Created, user)
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
