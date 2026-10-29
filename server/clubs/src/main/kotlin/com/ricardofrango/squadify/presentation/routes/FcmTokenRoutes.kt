package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.RegisterFcmTokenUseCase
import com.ricardofrango.squadify.domain.usecase.RemoveFcmTokenUseCase
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.RegisterFcmTokenRequest
import com.ricardofrango.squadify.model.RemoveFcmTokenRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.fcmTokenRoutes() {
    val registerFcmTokenUseCase by inject<RegisterFcmTokenUseCase>()
    val removeFcmTokenUseCase by inject<RemoveFcmTokenUseCase>()

    authenticate("auth-jwt") {
        route("/fcm-tokens") {
            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<RegisterFcmTokenRequest>()

                val success = registerFcmTokenUseCase(currentUserId, request.token)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to register push token"))
                }
            }

            // Token travels in the body, not the path -- FCM tokens are opaque strings up to ~4096
            // chars, not idiomatic (or necessarily URL-safe) as a path segment.
            delete {
                val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<RemoveFcmTokenRequest>()

                val success = removeFcmTokenUseCase(currentUserId, request.token)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to remove push token"))
                }
            }
        }
    }
}
