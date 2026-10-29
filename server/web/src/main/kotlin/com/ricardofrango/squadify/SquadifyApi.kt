package com.ricardofrango.squadify

import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.IllegalIdFormatException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.SquadifyException
import com.ricardofrango.squadify.exception.TooManyPasswordAttemptsException
import com.ricardofrango.squadify.exception.UnauthorizedException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.exception.WrongPasswordException
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.SERVER_TIME_HEADER
import com.ricardofrango.squadify.security.JwtTokens
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * Everything both servers' APIs share (TECHNICAL_DECISIONS §72): sign-in with [tokens] as `auth-jwt`,
 * JSON bodies, WebSockets for the live game, the status code and [ErrorResponse] for each error, and
 * the server-time header. A server then adds CORS if it has a web client, and its routes.
 */
fun Application.installSquadifyApi(tokens: JwtTokens, realm: String) {
    configureServerTime()

    install(Authentication) {
        jwt("auth-jwt") {
            this.realm = realm
            verifier(tokens.verifier)
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
            call.respond(status, ErrorResponse(cause.message, cause.code, (cause as? ConflictException)?.rows))
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
}

/**
 * The web client's CORS rules, kept apart from the rest so a test can check them without a database.
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
        // The browser hides any other header from the app's code unless it's exposed.
        exposeHeader(SERVER_TIME_HEADER)
    }
}

/** Every response carries the server's time ([SERVER_TIME_HEADER]), for the app's estimate of its clock. */
fun Application.configureServerTime() {
    install(ServerTimePlugin)
}

private val ServerTimePlugin = createApplicationPlugin("ServerTime") {
    onCall { call -> call.response.header(SERVER_TIME_HEADER, kotlin.time.Clock.System.now().toEpochMilliseconds()) }
}

private val LOCALHOST_ORIGIN = Regex("""http://(localhost|127\.0\.0\.1)(:\d+)?""")
