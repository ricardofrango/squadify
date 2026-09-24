package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.ChangePasswordUseCase
import com.ricardofrango.squadify.domain.usecase.GetCurrentUserUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateProfileUseCase
import com.ricardofrango.squadify.model.ChangePasswordRequest
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.UpdateProfileRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

// Self-scoped only -- every route here acts on the caller's own row (call.userId), never a path
// parameter for a target user, mirroring FcmTokenRoutes.kt's shape.
fun Route.userRoutes() {
    val getCurrentUserUseCase by inject<GetCurrentUserUseCase>()
    val updateProfileUseCase by inject<UpdateProfileUseCase>()
    val changePasswordUseCase by inject<ChangePasswordUseCase>()

    authenticate("auth-jwt") {
        route("/users/me") {
            get {
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getCurrentUserUseCase(currentUserId))
            }

            patch {
                val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<UpdateProfileRequest>()
                call.respond(updateProfileUseCase(currentUserId, request))
            }

            post("/password") {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<ChangePasswordRequest>()

                val success = changePasswordUseCase(currentUserId, request)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to change password"))
                }
            }
        }
    }
}
