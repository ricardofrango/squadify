package com.ricardofrango.footballmanager

import com.ricardofrango.footballmanager.data.database.DatabaseFactory
import com.ricardofrango.footballmanager.di.appModule
import com.ricardofrango.footballmanager.presentation.routes.authRoutes
import com.ricardofrango.footballmanager.presentation.routes.clubRoutes
import com.ricardofrango.footballmanager.presentation.routes.teamRoutes
import com.ricardofrango.footballmanager.presentation.routes.seasonRoutes
import com.ricardofrango.footballmanager.presentation.routes.ageGroupRoutes
import com.ricardofrango.footballmanager.presentation.routes.playerRoutes
import com.ricardofrango.footballmanager.security.JwtConfig
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.exception.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.ContentTransformationException
import kotlinx.serialization.SerializationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()

    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.subject != null) {
                    io.ktor.server.auth.jwt.JWTCredential(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }
        )
    }

    install(StatusPages) {
        exception<FootballManagerException> { call, cause ->
            val status = when (cause) {
                is UnauthorizedException -> HttpStatusCode.Unauthorized
                is ForbiddenException -> HttpStatusCode.Forbidden
                is ConflictException -> HttpStatusCode.Conflict
                is ValidationException -> HttpStatusCode.BadRequest
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, ErrorResponse(cause.message, cause.code))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body or parameters", "VAL_000"))
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body content", "VAL_000"))
        }
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid field values in request body", "VAL_000"))
        }
        exception<Throwable> { call, cause ->
            // Mask internal details for any other unhandled exceptions
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("An internal server error occurred", "-1")
            )
            // Log the actual cause for the developer (optional, usually handled by Ktor logging)
            cause.printStackTrace()
        }
    }

    routing {
        get("/") {
            call.respondText("Football Manager API is running")
        }
        authRoutes()
        clubRoutes()
        teamRoutes()
        seasonRoutes()
        ageGroupRoutes()
        playerRoutes()
    }
}
