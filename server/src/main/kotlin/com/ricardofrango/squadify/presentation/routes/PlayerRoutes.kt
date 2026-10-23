package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.*
import com.ricardofrango.squadify.model.*
import com.ricardofrango.squadify.security.userId
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import com.ricardofrango.squadify.util.LogoImage
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.playerRoutes() {
    val getPlayerUseCase by inject<GetPlayerUseCase>()
    val getPlayerSeasonStatsUseCase by inject<GetPlayerSeasonStatsUseCase>()
    val getPlayersUseCase by inject<GetPlayersUseCase>()
    val getAgeGroupPlayersByClubUseCase by inject<GetAgeGroupPlayersByClubUseCase>()
    val createPlayersUseCase by inject<CreatePlayersUseCase>()
    val updatePlayerUseCase by inject<UpdatePlayerUseCase>()
    val disablePlayerUseCase by inject<DisablePlayerUseCase>()
    val reactivatePlayerUseCase by inject<ReactivatePlayerUseCase>()
    val uploadPlayerPhotoUseCase by inject<UploadPlayerPhotoUseCase>()
    val deletePlayerPhotoUseCase by inject<DeletePlayerPhotoUseCase>()
    val getPlayerPhotoUseCase by inject<GetPlayerPhotoUseCase>()

    authenticate("auth-jwt") {
        route("/players") {
            // 1. Specific paths first to avoid UUID collision or matching ambiguity
            get("/associations/age-group") {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val ageGroupPlayers = getAgeGroupPlayersByClubUseCase(currentUserId, clubId)
                call.respond(ageGroupPlayers)
            }

            // 2. Generic base paths
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val teamId = call.request.queryParameters["teamId"]
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val players = getPlayersUseCase(currentUserId, clubId, teamId)
                call.respond(players)
            }

            post {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val playerRequest = call.receive<CreatePlayersRequest>()
                val player = createPlayersUseCase(currentUserId, playerRequest)
                call.respond(HttpStatusCode.Created, player)
            }

            // 3. ID-based paths last
            route("/{playerId}") {
                // Every season's per-match stats lines (design 30); totals are derived client-side.
                get("/season-stats") {
                    val playerId =
                        call.parameters["playerId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    call.respond(getPlayerSeasonStatsUseCase(currentUserId, playerId))
                }

                get {
                    val playerId =
                        call.parameters["playerId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val player = getPlayerUseCase(currentUserId, playerId)
                    call.respond(player)
                }

                patch {
                    val playerId = call.parameters["playerId"] ?: return@patch call.respond(
                        HttpStatusCode.BadRequest
                    )
                    val currentUserId =
                        call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<UpdatePlayerRequest>()

                    val success = updatePlayerUseCase(currentUserId, playerId, request)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to update player")
                        )
                    }
                }

                // Disabled, and out of every age group; the data stays (DisablePlayerUseCase).
                post("/disable") {
                    val playerId = call.parameters["playerId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    disablePlayerUseCase(currentUserId, playerId)
                    call.respond(HttpStatusCode.NoContent)
                }

                // Active again, so they can be added to a pool; the old pools aren't restored (ReactivatePlayerUseCase).
                post("/reactivate") {
                    val playerId = call.parameters["playerId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    reactivatePlayerUseCase(currentUserId, playerId)
                    call.respond(HttpStatusCode.NoContent)
                }

                // The photo: the image is the raw request body (PNG or JPEG, at most 5 MB). See PersonPhotoUseCases.
                put("/photo") {
                    val playerId = call.parameters["playerId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    // One byte over the limit is enough to know it's too big, without reading the rest.
                    val bytes = call.receiveChannel().readRemaining(LogoImage.MAX_UPLOAD_BYTES + 1L).readByteArray()
                    call.respond(PhotoResponse(uploadPlayerPhotoUseCase(currentUserId, playerId, bytes)))
                }

                delete("/photo") {
                    val playerId = call.parameters["playerId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    deletePlayerPhotoUseCase(currentUserId, playerId)
                    call.respond(HttpStatusCode.NoContent)
                }

                // A new image id on every upload, so the file never changes under a path: cache it for good.
                get("/photo/{file}") {
                    val playerId = call.parameters["playerId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val bytes = getPlayerPhotoUseCase(currentUserId, playerId, imageId)
                    call.response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
                    call.respondBytes(bytes, ContentType.Image.PNG)
                }
            }
        }
    }
}
