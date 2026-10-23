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
import com.ricardofrango.squadify.presentation.routes.matchRoutes
import com.ricardofrango.squadify.presentation.routes.opponentRoutes
import com.ricardofrango.squadify.presentation.routes.trainingRoutes
import com.ricardofrango.squadify.presentation.routes.fcmTokenRoutes
import com.ricardofrango.squadify.domain.usecase.SeedPlatformAdminUseCase
import com.ricardofrango.squadify.security.JwtConfig
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.exception.*
import com.ricardofrango.squadify.config.AppEnvironment
import com.ricardofrango.squadify.config.ServerConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.serverConfig
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.websocket.*
import kotlinx.serialization.SerializationException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlin.time.Duration.Companion.seconds
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080
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

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "club-manager"
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Token is invalid or expired", "AUTH_001"))
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

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        // The live-match socket (MatchRoutes.kt) is server-to-client broadcast only -- it never reads
        // `incoming` frames -- so there's no legitimate reason for a large inbound frame. Capped well
        // above any real LiveMatchUpdate payload (small JSON) but far below Long.MAX_VALUE, which let
        // a single oversized frame exhaust memory (see "issues to fix.md", "Unbounded WebSocket frame
        // size").
        maxFrameSize = 64 * 1024L
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }

    install(StatusPages) {
        exception<SquadifyException> { call, cause ->
            cause.printStackTrace()
            val status = when (cause) {
                is UnauthorizedException -> HttpStatusCode.Unauthorized
                is ForbiddenException -> HttpStatusCode.Forbidden
                is WrongPasswordException -> HttpStatusCode.BadRequest
                is TooManyPasswordAttemptsException -> HttpStatusCode.Forbidden
                is NotFoundException -> HttpStatusCode.NotFound
                is ConflictException -> HttpStatusCode.Conflict
                is ValidationException -> HttpStatusCode.BadRequest
                is IllegalIdFormatException -> HttpStatusCode.BadRequest
                is DatabaseException -> HttpStatusCode.InternalServerError
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, ErrorResponse(cause.message, cause.code))
        }
        exception<BadRequestException> { call, cause ->
            cause.printStackTrace()
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body or parameters", "VAL_000"))
        }
        exception<ContentTransformationException> { call, cause ->
            cause.printStackTrace()
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body content", "VAL_000"))
        }
        exception<SerializationException> { call, cause ->
            cause.printStackTrace()
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid field values in request body", "VAL_000"))
        }
        exception<Throwable> { call, cause ->
            // Log the actual cause for the developer (optional, usually handled by Ktor logging)
            cause.printStackTrace()
            // Mask internal details for any other unhandled exceptions
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("An internal server error occurred", "-1")
            )
        }
    }

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
        }
    }
}

/**
 * The web client's CORS rules, kept apart from [module] so a test can check them without a database.
 * Every method a repository uses has to be listed: a browser refuses anything else at the preflight
 * and the app only sees "Failed to fetch" (PUT was missing, so saving a match report, a training plan,
 * session ratings or exercises failed on web only).
 *
 * In dev ([allowAnyLocalhost]) the web app is also accepted from localhost on any port: the webpack
 * dev server moves to the next free port (8082, ...) when 8081 is taken, and the browser then sent an
 * origin the server refused.
 */
fun Application.configureCors(webAppBaseUrl: String, allowAnyLocalhost: Boolean = false) {
    install(CORS) {
        if (allowAnyLocalhost) {
            allowOrigins { origin -> LOCALHOST_ORIGIN.matches(origin) }
        }
        val webAppUri = java.net.URI(webAppBaseUrl)
        val webAppAuthority = webAppUri.authority
            ?: throw IllegalStateException(
                "WEBAPP_BASE_URL (\"${webAppBaseUrl}\") must be a full URL including a " +
                    "scheme, e.g. \"https://example.com\" -- CORS can't determine the allowed origin's " +
                    "host from a value with no scheme."
            )
        allowHost(webAppAuthority, schemes = listOf(webAppUri.scheme))
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowNonSimpleContentTypes = true
    }
}

private val LOCALHOST_ORIGIN = Regex("""http://(localhost|127\.0\.0\.1)(:\d+)?""")
