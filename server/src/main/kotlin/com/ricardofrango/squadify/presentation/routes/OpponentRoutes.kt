package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.*
import com.ricardofrango.squadify.model.*
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
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject

fun Route.opponentRoutes() {
    val createOpponentUseCase by inject<CreateOpponentUseCase>()
    val getOpponentsUseCase by inject<GetOpponentsUseCase>()
    val updateOpponentUseCase by inject<UpdateOpponentUseCase>()
    val getOpponentListUseCase by inject<GetOpponentListUseCase>()
    val getOpponentDetailsUseCase by inject<GetOpponentDetailsUseCase>()
    val uploadOpponentLogoUseCase by inject<UploadOpponentLogoUseCase>()
    val deleteOpponentLogoUseCase by inject<DeleteOpponentLogoUseCase>()
    val getOpponentLogoUseCase by inject<GetOpponentLogoUseCase>()

    authenticate("auth-jwt") {
        route("/opponents") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val ageGroupId = call.request.queryParameters["ageGroupId"]
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val opponents = getOpponentsUseCase(currentUserId, clubId, ageGroupId)
                call.respond(opponents)
            }

            // The Opponents screen: the list plus its Recent section and whether the caller can create.
            get("/list") {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val ageGroupId = call.request.queryParameters["ageGroupId"]
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getOpponentListUseCase(currentUserId, clubId, ageGroupId))
            }

            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val clubId = call.request.queryParameters["clubId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val request = call.receive<CreateOpponentRequest>()
                val opponent = createOpponentUseCase(currentUserId, clubId, request)
                call.respond(HttpStatusCode.Created, opponent)
            }

            // Opponent details: the opponent and every match against them.
            get("/{opponentId}") {
                val opponentId = call.parameters["opponentId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getOpponentDetailsUseCase(currentUserId, opponentId))
            }

            patch("/{opponentId}") {
                val opponentId = call.parameters["opponentId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<UpdateOpponentRequest>()

                val success = updateOpponentUseCase(currentUserId, opponentId, request)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update opponent"))
                }
            }

            // The logo, like the club's: the image is the raw request body (PNG or JPEG, at most 5 MB).
            // ageGroupId lets that age group's coaches in, as when creating the opponent from a match.
            put("/{opponentId}/logo") {
                val opponentId = call.parameters["opponentId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val ageGroupId = call.request.queryParameters["ageGroupId"]
                // One byte over the limit is enough to know it's too big, without reading the rest.
                val bytes = call.receiveChannel().readRemaining(LogoImage.MAX_UPLOAD_BYTES + 1L).readByteArray()
                call.respond(LogoResponse(uploadOpponentLogoUseCase(currentUserId, opponentId, ageGroupId, bytes)))
            }

            delete("/{opponentId}/logo") {
                val opponentId = call.parameters["opponentId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                deleteOpponentLogoUseCase(currentUserId, opponentId, call.request.queryParameters["ageGroupId"])
                call.respond(HttpStatusCode.NoContent)
            }

            // A new image id on every upload, so the file never changes under a path: cache it for good.
            get("/{opponentId}/logo/{file}") {
                val opponentId = call.parameters["opponentId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
                val bytes = getOpponentLogoUseCase(currentUserId, opponentId, imageId)
                call.response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
                call.respondBytes(bytes, ContentType.Image.PNG)
            }
        }
    }
}
