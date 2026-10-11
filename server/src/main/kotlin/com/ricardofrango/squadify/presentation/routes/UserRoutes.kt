package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.ChangePasswordUseCase
import com.ricardofrango.squadify.domain.usecase.ConfirmEmailChangeUseCase
import com.ricardofrango.squadify.domain.usecase.RequestEmailChangeUseCase
import com.ricardofrango.squadify.model.EmailChangeConfirmRequest
import com.ricardofrango.squadify.model.EmailChangeRequest
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
    val requestEmailChangeUseCase by inject<RequestEmailChangeUseCase>()
    val confirmEmailChangeUseCase by inject<ConfirmEmailChangeUseCase>()

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

            // Change email: a code goes to the new address (202), then confirming it switches the
            // account over and answers the updated user.
            post("/email/request") {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                requestEmailChangeUseCase(currentUserId, call.receive<EmailChangeRequest>())
                call.respond(HttpStatusCode.Accepted)
            }

            post("/email/confirm") {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                call.respond(confirmEmailChangeUseCase(currentUserId, call.receive<EmailChangeConfirmRequest>()))
            }
        }
    }
}
