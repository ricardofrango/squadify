package com.ricardofrango.squadify.coach.routes

import com.ricardofrango.squadify.domain.usecase.LoginUseCase
import com.ricardofrango.squadify.domain.usecase.RefreshTokenUseCase
import com.ricardofrango.squadify.domain.usecase.RegisterUseCase
import com.ricardofrango.squadify.model.LoginRequest
import com.ricardofrango.squadify.model.RefreshTokenRequest
import com.ricardofrango.squadify.model.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/** Sign-up, sign-in and refresh, as on the clubs server (same requests and responses). */
fun Route.authRoutes() {
    val registerUseCase by inject<RegisterUseCase>()
    val loginUseCase by inject<LoginUseCase>()
    val refreshTokenUseCase by inject<RefreshTokenUseCase>()

    route("/auth") {
        post("/register") {
            call.respond(HttpStatusCode.Created, registerUseCase(call.receive<RegisterRequest>()))
        }
        post("/login") {
            call.respond(loginUseCase(call.receive<LoginRequest>()))
        }
        post("/refresh") {
            call.respond(refreshTokenUseCase(call.receive<RefreshTokenRequest>().refreshToken))
        }
    }
}
