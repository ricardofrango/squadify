package com.sportssoftw.squadify.presentation.routes

import com.sportssoftw.squadify.domain.usecase.LoginUseCase
import com.sportssoftw.squadify.domain.usecase.RefreshTokenUseCase
import com.sportssoftw.squadify.domain.usecase.RegisterUseCase
import com.sportssoftw.squadify.model.ErrorResponse
import com.sportssoftw.squadify.model.LoginRequest
import com.sportssoftw.squadify.model.RefreshTokenRequest
import com.sportssoftw.squadify.model.RegisterRequest
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
