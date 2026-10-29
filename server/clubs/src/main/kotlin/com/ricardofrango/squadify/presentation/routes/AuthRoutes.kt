package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.LoginUseCase
import com.ricardofrango.squadify.domain.usecase.RefreshTokenUseCase
import com.ricardofrango.squadify.domain.usecase.RegisterUseCase
import com.ricardofrango.squadify.domain.usecase.RequestPasswordResetUseCase
import com.ricardofrango.squadify.domain.usecase.ResetPasswordUseCase
import com.ricardofrango.squadify.model.PasswordResetConfirmRequest
import com.ricardofrango.squadify.model.PasswordResetRequest
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.LoginRequest
import com.ricardofrango.squadify.model.RefreshTokenRequest
import com.ricardofrango.squadify.model.RegisterRequest
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
    val requestPasswordResetUseCase by inject<RequestPasswordResetUseCase>()
    val resetPasswordUseCase by inject<ResetPasswordUseCase>()

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

        // Forgot password. 202 when a code was sent (or one sent under a minute ago is still pending),
        // 404 when the email has no active account.
        post("/password-reset/request") {
            val request = call.receive<PasswordResetRequest>()
            requestPasswordResetUseCase(request.email)
            call.respond(HttpStatusCode.Accepted)
        }

        post("/password-reset/confirm") {
            val request = call.receive<PasswordResetConfirmRequest>()
            resetPasswordUseCase(request)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
