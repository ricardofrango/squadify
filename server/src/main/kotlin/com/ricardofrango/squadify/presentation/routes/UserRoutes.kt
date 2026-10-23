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
import com.ricardofrango.squadify.domain.usecase.DeleteUserAvatarUseCase
import com.ricardofrango.squadify.domain.usecase.GetUserAvatarUseCase
import com.ricardofrango.squadify.domain.usecase.UploadUserAvatarUseCase
import com.ricardofrango.squadify.model.PhotoResponse
import com.ricardofrango.squadify.security.userId
import com.ricardofrango.squadify.util.LogoImage
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

// Self-scoped only -- every route that changes something acts on the caller's own row (call.userId),
// never a path parameter for a target user, mirroring FcmTokenRoutes.kt's shape. The one exception is
// reading someone's profile photo (GET /users/{userId}/avatar/{file}).
fun Route.userRoutes() {
    val getCurrentUserUseCase by inject<GetCurrentUserUseCase>()
    val updateProfileUseCase by inject<UpdateProfileUseCase>()
    val changePasswordUseCase by inject<ChangePasswordUseCase>()
    val requestEmailChangeUseCase by inject<RequestEmailChangeUseCase>()
    val confirmEmailChangeUseCase by inject<ConfirmEmailChangeUseCase>()
    val uploadUserAvatarUseCase by inject<UploadUserAvatarUseCase>()
    val deleteUserAvatarUseCase by inject<DeleteUserAvatarUseCase>()
    val getUserAvatarUseCase by inject<GetUserAvatarUseCase>()

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

            // The caller's profile photo: the image is the raw request body (PNG or JPEG, at most 5 MB).
            put("/avatar") {
                val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                // One byte over the limit is enough to know it's too big, without reading the rest.
                val bytes = call.receiveChannel().readRemaining(LogoImage.MAX_UPLOAD_BYTES + 1L).readByteArray()
                call.respond(PhotoResponse(uploadUserAvatarUseCase(currentUserId, bytes)))
            }

            delete("/avatar") {
                val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                deleteUserAvatarUseCase(currentUserId)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // Anyone's profile photo, for any signed-in user (see GetUserAvatarUseCase). A new image id on
        // every upload, so the file never changes under a path: cache it for good.
        get("/users/{userId}/avatar/{file}") {
            if (call.userId == null) return@get call.respond(HttpStatusCode.Unauthorized)
            val ownerId = call.parameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
            val bytes = getUserAvatarUseCase(ownerId, imageId)
            call.response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
            call.respondBytes(bytes, ContentType.Image.PNG)
        }
    }
}
