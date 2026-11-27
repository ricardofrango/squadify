package com.sportssoftw.squadcall.presentation.routes

import com.sportssoftw.squadcall.domain.usecase.LoginUseCase
import com.sportssoftw.squadcall.domain.usecase.RefreshTokenUseCase
import com.sportssoftw.squadcall.domain.usecase.RegisterUseCase
import com.sportssoftw.squadcall.model.ErrorResponse
import com.sportssoftw.squadcall.model.LoginRequest
import com.sportssoftw.squadcall.model.RefreshTokenRequest
import com.sportssoftw.squadcall.model.RegisterRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

// Forgot password is in the shared accountRoutes (:server:web).
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
