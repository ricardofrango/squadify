package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.config.CoachConfig
import com.ricardofrango.squadify.coach.data.CoachDatabase
import com.ricardofrango.squadify.coach.di.CoachTokens
import com.ricardofrango.squadify.coach.di.coachModule
import com.ricardofrango.squadify.coach.routes.authRoutes
import com.ricardofrango.squadify.coach.routes.spaceRoutes
import com.ricardofrango.squadify.coach.routes.structureRoutes
import com.ricardofrango.squadify.installSquadifyApi
import com.ricardofrango.squadify.presentation.routes.gameRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.dsl.koinApplication
import org.koin.ktor.plugin.setKoinApplication
import org.koin.logger.slf4jLogger

fun main() {
    embeddedServer(Netty, port = CoachConfig.port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

/** The Squadify for Coaches API (TECHNICAL_DECISIONS §72). Android and iOS only, so no CORS. */
fun Application.module() {
    // As on the clubs server, the module owns its connection pool and Koin container and closes them
    // when it stops.
    val connectionPool = CoachDatabase.init()
    val koinApplication = koinApplication {
        slf4jLogger()
        modules(coachModule)
    }
    setKoinApplication(koinApplication)
    monitor.subscribe(ApplicationStopped) { stopped ->
        if (stopped === this) {
            koinApplication.close()
            connectionPool.close()
        }
    }

    installSquadifyApi(CoachTokens, realm = "squadify-coach")

    routing {
        get("/") { call.respondText("Squadify for Coaches API is running") }
        route("/v1") {
            authRoutes()
            spaceRoutes()
            structureRoutes()
            gameRoutes(CoachTokens)
        }
    }
}
