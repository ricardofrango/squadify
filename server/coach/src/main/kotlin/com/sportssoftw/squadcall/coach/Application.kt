package com.sportssoftw.squadcall.coach

import com.sportssoftw.squadcall.coach.config.CoachConfig
import com.sportssoftw.squadcall.coach.data.CoachDatabase
import com.sportssoftw.squadcall.coach.di.CoachTokens
import com.sportssoftw.squadcall.coach.di.coachModule
import com.sportssoftw.squadcall.coach.routes.accountDeletionRoutes
import com.sportssoftw.squadcall.coach.routes.adminRoutes
import com.sportssoftw.squadcall.coach.routes.authRoutes
import com.sportssoftw.squadcall.coach.routes.spaceRoutes
import com.sportssoftw.squadcall.coach.routes.storeRoutes
import com.sportssoftw.squadcall.coach.routes.staffRoutes
import com.sportssoftw.squadcall.coach.routes.structureRoutes
import com.sportssoftw.squadcall.installSquadcallApi
import com.sportssoftw.squadcall.presentation.routes.accountRoutes
import com.sportssoftw.squadcall.presentation.routes.fcmTokenRoutes
import com.sportssoftw.squadcall.presentation.routes.gameRoutes
import com.sportssoftw.squadcall.presentation.routes.trainingRoutes
import com.sportssoftw.squadcall.presentation.routes.ageGroupImportRoutes
import com.sportssoftw.squadcall.presentation.routes.featureFlagRoutes
import com.sportssoftw.squadcall.presentation.AppMinimums
import com.sportssoftw.squadcall.model.FeatureFlags
import com.sportssoftw.squadcall.presentation.routes.ImportSources
import com.sportssoftw.squadcall.presentation.routes.imageRoutes
import com.sportssoftw.squadcall.presentation.routes.publicImageRoutes
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

/** The Squadcall for Coaches API (TECHNICAL_DECISIONS §72). Android and iOS only, so no CORS. */
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

    installSquadcallApi(CoachTokens, realm = "squadcall-coach", minimums = AppMinimums(android = CoachConfig.minimumAndroidBuild, ios = CoachConfig.minimumIosBuild))

    routing {
        get("/") { call.respondText("Squadcall for Coaches API is running") }
        route("/v1") {
            authRoutes()
            accountRoutes()
            accountDeletionRoutes()
            fcmTokenRoutes()
            spaceRoutes()
            structureRoutes()
            staffRoutes()
            adminRoutes()
            storeRoutes()
            imageRoutes()
            publicImageRoutes()
            gameRoutes(CoachTokens)
            trainingRoutes()
            ageGroupImportRoutes(ImportSources(CoachConfig.importSources, CoachConfig.crossEnvironmentImportSources, CoachConfig.platformAdminEmails), importEnabled = CoachConfig.featureAgeGroupImport)
            featureFlagRoutes(FeatureFlags(ageGroupImport = CoachConfig.featureAgeGroupImport, cacheSeconds = CoachConfig.clientCacheSeconds, matchCacheSeconds = CoachConfig.clientMatchCacheSeconds, plansEnabled = CoachConfig.plansEnabled), AppMinimums(android = CoachConfig.minimumAndroidBuild, ios = CoachConfig.minimumIosBuild))
        }
    }
}
