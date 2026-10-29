package com.ricardofrango.squadify

import com.ricardofrango.squadify.data.database.DatabaseFactory
import com.ricardofrango.squadify.di.appModule
import com.ricardofrango.squadify.presentation.routes.authRoutes
import com.ricardofrango.squadify.presentation.routes.userRoutes
import com.ricardofrango.squadify.presentation.routes.clubRoutes
import com.ricardofrango.squadify.presentation.routes.dashboardRoutes
import com.ricardofrango.squadify.presentation.routes.teamRoutes
import com.ricardofrango.squadify.presentation.routes.seasonRoutes
import com.ricardofrango.squadify.presentation.routes.ageGroupRoutes
import com.ricardofrango.squadify.presentation.routes.playerRoutes
import com.ricardofrango.squadify.presentation.routes.publicImageRoutes
import com.ricardofrango.squadify.presentation.routes.matchRoutes
import com.ricardofrango.squadify.presentation.routes.opponentRoutes
import com.ricardofrango.squadify.presentation.routes.trainingRoutes
import com.ricardofrango.squadify.presentation.routes.fcmTokenRoutes
import com.ricardofrango.squadify.domain.usecase.SeedPlatformAdminUseCase
import com.ricardofrango.squadify.security.JwtConfig
import io.ktor.server.response.header
import com.ricardofrango.squadify.config.AppEnvironment
import com.ricardofrango.squadify.config.ServerConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.serverConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.getKoin
import org.koin.dsl.koinApplication
import org.koin.ktor.plugin.setKoinApplication
import org.koin.logger.slf4jLogger

fun main() {
    // The module is named in the configuration, not passed as a function reference: in development
    // (`./gradlew :server:run` sets io.ktor.development) the server reloads its own code whenever the
    // compiled classes change -- keep `./gradlew -t :server:classes` running next to it -- and Ktor
    // can only reload a module it finds by name. Elsewhere the watch is off. See TECHNICAL_DECISIONS.md §59.
    val environment = applicationEnvironment {
        config = MapApplicationConfig().apply { put("ktor.application.modules", listOf("com.ricardofrango.squadify.ApplicationKt.module")) }
    }
    embeddedServer(
        Netty,
        rootConfig = serverConfig(environment) { watchPaths = listOf("classes", "resources") },
        configure = {
            connector {
                port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080
                host = "0.0.0.0"
            }
        }
    ).start(wait = true)
}

fun Application.module() {
    // Each module owns its connection pool and its Koin container (not the process-wide one), and
    // closes them when *it* stops. In development a reload starts the new module before stopping the
    // old one, and the old one's ApplicationStopped reaches the new one's subscribers too -- so the
    // check on which application stopped, and no Koin plugin (it closes its container on any stop).
    val connectionPool = DatabaseFactory.init()
    val koinApplication = koinApplication {
        slf4jLogger()
        modules(appModule)
    }
    setKoinApplication(koinApplication)
    monitor.subscribe(ApplicationStopped) { stopped ->
        if (stopped === this) {
            koinApplication.close()
            connectionPool.close()
        }
    }

    // One-shot: guarantee a platform-admin account exists so a freshly-wiped dev database is usable
    // immediately, with no manual bootstrapping. Idempotent -- a no-op when the admin already exists
    // or when seeding is disabled (see SeedPlatformAdminUseCase / ServerConfig).
    runBlocking { getKoin().get<SeedPlatformAdminUseCase>().invoke() }

    // Only the web client is subject to CORS -- Android/iOS/Desktop don't send an Origin header, so
    // this exists purely for the browser build. Reuses WEBAPP_BASE_URL (already set for email links --
    // see ServerConfig.webAppBaseUrl) as the one allowed origin, since it's already exactly "this
    // environment's web client URL". java.net.URI.authority preserves the string's own port (or lack of
    // one) as written, unlike Ktor's Url.hostWithPort, which always forces one in -- allowHost needs to
    // match the browser's actual Origin header ("https://host", no ":443") exactly.
    configureCors(ServerConfig.webAppBaseUrl, allowAnyLocalhost = ServerConfig.environment == AppEnvironment.DEV)
    installSquadifyApi(JwtConfig, realm = "club-manager")

    routing {
        // Unversioned -- infra/uptime checks hit this directly, no reason to make them know the API version.
        get("/") {
            call.respondText("Squadify API is running")
        }
        route("/v1") {
            authRoutes()
            userRoutes()
            clubRoutes()
            dashboardRoutes()
            teamRoutes()
            seasonRoutes()
            ageGroupRoutes()
            playerRoutes()
            matchRoutes()
            opponentRoutes()
            trainingRoutes()
            fcmTokenRoutes()
            publicImageRoutes()
        }
    }
}
