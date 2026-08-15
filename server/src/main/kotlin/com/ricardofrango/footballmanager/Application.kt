package com.ricardofrango.footballmanager

import com.ricardofrango.footballmanager.data.database.DatabaseFactory
import com.ricardofrango.footballmanager.di.appModule
import com.ricardofrango.footballmanager.presentation.routes.authRoutes
import com.ricardofrango.footballmanager.presentation.routes.clubRoutes
import com.ricardofrango.footballmanager.security.JwtConfig
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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

    routing {
        get("/") {
            call.respondText("Football Manager API is running")
        }
        authRoutes()
        clubRoutes()
    }
}
