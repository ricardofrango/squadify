package com.ricardofrango.squadify.presentation.routes

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

// The profile photo (the rest of the account is the shared accountRoutes, :server:web, on both servers).
// Self-scoped only -- every route that changes something acts on the caller's own row (call.userId),
// never a path parameter for a target user, mirroring FcmTokenRoutes.kt's shape. The one exception is
// reading someone's profile photo (GET /users/{userId}/avatar/{file}).
fun Route.userRoutes() {
    val uploadUserAvatarUseCase by inject<UploadUserAvatarUseCase>()
    val deleteUserAvatarUseCase by inject<DeleteUserAvatarUseCase>()
    val getUserAvatarUseCase by inject<GetUserAvatarUseCase>()

    authenticate("auth-jwt") {
        route("/users/me") {
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
