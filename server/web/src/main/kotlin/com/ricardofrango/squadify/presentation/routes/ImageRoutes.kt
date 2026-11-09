package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.DeleteClubLogoUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteOpponentLogoUseCase
import com.ricardofrango.squadify.domain.usecase.DeletePlayerPhotoUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteUserAvatarUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubLogoUseCase
import com.ricardofrango.squadify.domain.usecase.GetOpponentLogoUseCase
import com.ricardofrango.squadify.domain.usecase.UploadAgeGroupLogoUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteAgeGroupLogoUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupLogoUseCase
import com.ricardofrango.squadify.domain.usecase.GetPlayerPhotoUseCase
import com.ricardofrango.squadify.domain.usecase.GetUserAvatarUseCase
import com.ricardofrango.squadify.domain.usecase.UploadClubLogoUseCase
import com.ricardofrango.squadify.domain.usecase.UploadOpponentLogoUseCase
import com.ricardofrango.squadify.domain.usecase.UploadPlayerPhotoUseCase
import com.ricardofrango.squadify.domain.usecase.UploadUserAvatarUseCase
import com.ricardofrango.squadify.model.LogoResponse
import com.ricardofrango.squadify.model.PhotoResponse
import com.ricardofrango.squadify.security.userId
import com.ricardofrango.squadify.util.LogoImage
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject

/**
 * Uploading, removing and reading images, on both servers (TECHNICAL_DECISIONS §57, §72): a club's logo
 * (a coach's space is a club), an opponent's logo, a player's photo and the caller's own photo. An
 * upload is the raw request body (PNG or JPEG, at most 5 MB). A new image id on every upload, so the
 * file never changes under a path and an app can cache it for good. Who may do what is the server's
 * `AccessPolicy`, asked by the use cases.
 */
fun Route.imageRoutes() {
    val uploadClubLogoUseCase by inject<UploadClubLogoUseCase>()
    val deleteClubLogoUseCase by inject<DeleteClubLogoUseCase>()
    val getClubLogoUseCase by inject<GetClubLogoUseCase>()
    val uploadOpponentLogoUseCase by inject<UploadOpponentLogoUseCase>()
    val deleteOpponentLogoUseCase by inject<DeleteOpponentLogoUseCase>()
    val getOpponentLogoUseCase by inject<GetOpponentLogoUseCase>()
    val uploadAgeGroupLogoUseCase by inject<UploadAgeGroupLogoUseCase>()
    val deleteAgeGroupLogoUseCase by inject<DeleteAgeGroupLogoUseCase>()
    val getAgeGroupLogoUseCase by inject<GetAgeGroupLogoUseCase>()
    val uploadPlayerPhotoUseCase by inject<UploadPlayerPhotoUseCase>()
    val deletePlayerPhotoUseCase by inject<DeletePlayerPhotoUseCase>()
    val getPlayerPhotoUseCase by inject<GetPlayerPhotoUseCase>()
    val uploadUserAvatarUseCase by inject<UploadUserAvatarUseCase>()
    val deleteUserAvatarUseCase by inject<DeleteUserAvatarUseCase>()
    val getUserAvatarUseCase by inject<GetUserAvatarUseCase>()

    authenticate("auth-jwt") {
        route("/clubs/{clubId}/logo") {
            put {
                val clubId = call.parameters["clubId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                call.respond(LogoResponse(uploadClubLogoUseCase(currentUserId, clubId, call.receiveImage())))
            }
            delete {
                val clubId = call.parameters["clubId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                deleteClubLogoUseCase(currentUserId, clubId)
                call.respond(HttpStatusCode.NoContent)
            }
            get("/{file}") {
                val clubId = call.parameters["clubId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respondImage(getClubLogoUseCase(currentUserId, clubId, imageId))
            }
        }

        // The crest of the club an age group plays for (the coach app).
        route("/age-groups/{ageGroupId}/logo") {
            put {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                call.respond(LogoResponse(uploadAgeGroupLogoUseCase(currentUserId, ageGroupId, call.receiveImage())))
            }
            delete {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                deleteAgeGroupLogoUseCase(currentUserId, ageGroupId)
                call.respond(HttpStatusCode.NoContent)
            }
            get("/{file}") {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respondImage(getAgeGroupLogoUseCase(currentUserId, ageGroupId, imageId))
            }
        }

        // ageGroupId lets that age group's coaches in, as when creating the opponent from a match.
        route("/opponents/{opponentId}/logo") {
            put {
                val opponentId = call.parameters["opponentId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val ageGroupId = call.request.queryParameters["ageGroupId"]
                call.respond(LogoResponse(uploadOpponentLogoUseCase(currentUserId, opponentId, ageGroupId, call.receiveImage())))
            }
            delete {
                val opponentId = call.parameters["opponentId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                deleteOpponentLogoUseCase(currentUserId, opponentId, call.request.queryParameters["ageGroupId"])
                call.respond(HttpStatusCode.NoContent)
            }
            get("/{file}") {
                val opponentId = call.parameters["opponentId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respondImage(getOpponentLogoUseCase(currentUserId, opponentId, imageId))
            }
        }

        route("/players/{playerId}/photo") {
            put {
                val playerId = call.parameters["playerId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                call.respond(PhotoResponse(uploadPlayerPhotoUseCase(currentUserId, playerId, call.receiveImage())))
            }
            delete {
                val playerId = call.parameters["playerId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                deletePlayerPhotoUseCase(currentUserId, playerId)
                call.respond(HttpStatusCode.NoContent)
            }
            get("/{file}") {
                val playerId = call.parameters["playerId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
                call.respondImage(getPlayerPhotoUseCase(currentUserId, playerId, imageId))
            }
        }

        // The caller's own photo: only ever their own row (call.userId), never a user from the path.
        route("/users/me/avatar") {
            put {
                val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                call.respond(PhotoResponse(uploadUserAvatarUseCase(currentUserId, call.receiveImage())))
            }
            delete {
                val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                deleteUserAvatarUseCase(currentUserId)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        // Anyone's profile photo, for any signed-in user (see GetUserAvatarUseCase).
        get("/users/{userId}/avatar/{file}") {
            if (call.userId == null) return@get call.respond(HttpStatusCode.Unauthorized)
            val ownerId = call.parameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
            call.respondImage(getUserAvatarUseCase(ownerId, imageId))
        }
    }
}

// One byte over the limit is enough to know it's too big, without reading the rest.
private suspend fun ApplicationCall.receiveImage(): ByteArray =
    receiveChannel().readRemaining(LogoImage.MAX_UPLOAD_BYTES + 1L).readByteArray()

private suspend fun ApplicationCall.respondImage(bytes: ByteArray) {
    response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
    respondBytes(bytes, ContentType.Image.PNG)
}
