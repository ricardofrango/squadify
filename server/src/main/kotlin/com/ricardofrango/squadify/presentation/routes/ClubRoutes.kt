package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.AddClubMembersUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubHomeUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubMatchesUpdatesUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubMembersUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubMembersOverviewUseCase
import com.ricardofrango.squadify.domain.usecase.ManageClubInviteUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateClubMemberRoleUseCase
import com.ricardofrango.squadify.domain.usecase.RemoveClubMemberUseCase
import com.ricardofrango.squadify.model.UpdateClubMemberRoleRequest
import com.ricardofrango.squadify.domain.usecase.GetClubPermissionsUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubsUseCase
import com.ricardofrango.squadify.domain.usecase.ProvisionClubUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateClubPermissionsUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateClubUseCase
import com.ricardofrango.squadify.model.AddClubMembersRequest
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.ProvisionClubRequest
import com.ricardofrango.squadify.model.UpdateClubPermissionsRequest
import com.ricardofrango.squadify.model.UpdateClubRequest
import com.ricardofrango.squadify.security.JwtConfig
import com.ricardofrango.squadify.security.userId
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.put
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import com.ricardofrango.squadify.domain.usecase.UploadClubLogoUseCase
import com.ricardofrango.squadify.domain.usecase.DeleteClubLogoUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubLogoUseCase
import com.ricardofrango.squadify.model.ClubLogoResponse
import com.ricardofrango.squadify.util.LogoImage
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject

fun Route.clubRoutes() {
    val getClubsUseCase by inject<GetClubsUseCase>()
    val getClubDetailsUseCase by inject<GetClubDetailsUseCase>()
    val getClubHomeUseCase by inject<GetClubHomeUseCase>()
    val provisionClubUseCase by inject<ProvisionClubUseCase>()
    val addClubMembersUseCase by inject<AddClubMembersUseCase>()
    val getClubMembersUseCase by inject<GetClubMembersUseCase>()
    val getClubMembersOverviewUseCase by inject<GetClubMembersOverviewUseCase>()
    val updateClubMemberRoleUseCase by inject<UpdateClubMemberRoleUseCase>()
    val removeClubMemberUseCase by inject<RemoveClubMemberUseCase>()
    val manageClubInviteUseCase by inject<ManageClubInviteUseCase>()
    val updateClubUseCase by inject<UpdateClubUseCase>()
    val getClubMatchesUpdatesUseCase by inject<GetClubMatchesUpdatesUseCase>()
    val getClubPermissionsUseCase by inject<GetClubPermissionsUseCase>()
    val updateClubPermissionsUseCase by inject<UpdateClubPermissionsUseCase>()
    val uploadClubLogoUseCase by inject<UploadClubLogoUseCase>()
    val deleteClubLogoUseCase by inject<DeleteClubLogoUseCase>()
    val getClubLogoUseCase by inject<GetClubLogoUseCase>()

    authenticate("auth-jwt") {
        route("/clubs") {
            get {
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val clubs = getClubsUseCase(currentUserId)
                call.respond(clubs)
            }

            post("/provision") {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<ProvisionClubRequest>()

                val club = provisionClubUseCase(currentUserId, request)
                call.respond(HttpStatusCode.Created, club)
            }

            route("/{clubId}") {
                get {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val club = getClubDetailsUseCase(currentUserId, clubId)
                    call.respond(club)
                }

                get("/home") {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    call.respond(getClubHomeUseCase(currentUserId, clubId))
                }

                patch {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<UpdateClubRequest>()

                    val success = updateClubUseCase(currentUserId, clubId, request)
                    if (success) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to update club")
                        )
                    }
                }

                // The logo: the image is the raw request body (PNG or JPEG, at most 5 MB). See ClubLogoUseCases.
                put("/logo") {
                    val clubId = call.parameters["clubId"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@put call.respond(HttpStatusCode.Unauthorized)
                    // One byte over the limit is enough to know it's too big, without reading the rest.
                    val bytes = call.receiveChannel().readRemaining(LogoImage.MAX_UPLOAD_BYTES + 1L).readByteArray()
                    call.respond(ClubLogoResponse(uploadClubLogoUseCase(currentUserId, clubId, bytes)))
                }

                delete("/logo") {
                    val clubId = call.parameters["clubId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    deleteClubLogoUseCase(currentUserId, clubId)
                    call.respond(HttpStatusCode.NoContent)
                }

                // A new image id on every upload, so the file never changes under a path: cache it for good.
                get("/logo/{file}") {
                    val clubId = call.parameters["clubId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val bytes = getClubLogoUseCase(currentUserId, clubId, imageId)
                    call.response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
                    call.respondBytes(bytes, ContentType.Image.PNG)
                }

                post("/members") {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<AddClubMembersRequest>()

                    val success = addClubMembersUseCase(currentUserId, clubId, request)
                    if (success) {
                        call.respond(HttpStatusCode.Created)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to add club members")
                        )
                    }
                }

                get("/members") {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    call.respond(getClubMembersUseCase(currentUserId, clubId))
                }

                // Members with their roles, age-group roles and linked players, plus pending invites
                // (Club settings → Members, Member details: designs 34, 35).
                get("/members/overview") {
                    val clubId = call.parameters["clubId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    call.respond(getClubMembersOverviewUseCase(currentUserId, clubId))
                }

                patch("/members/{memberUserId}") {
                    val clubId = call.parameters["clubId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val memberUserId = call.parameters["memberUserId"] ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    updateClubMemberRoleUseCase(currentUserId, clubId, memberUserId, call.receive<UpdateClubMemberRoleRequest>())
                    call.respond(HttpStatusCode.NoContent)
                }

                delete("/members/{memberUserId}") {
                    val clubId = call.parameters["clubId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val memberUserId = call.parameters["memberUserId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    removeClubMemberUseCase(currentUserId, clubId, memberUserId)
                    call.respond(HttpStatusCode.NoContent)
                }

                post("/invites/{inviteId}/resend") {
                    val clubId = call.parameters["clubId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val inviteId = call.parameters["inviteId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    manageClubInviteUseCase.resend(currentUserId, clubId, inviteId)
                    call.respond(HttpStatusCode.NoContent)
                }

                delete("/invites/{inviteId}") {
                    val clubId = call.parameters["clubId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val inviteId = call.parameters["inviteId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    manageClubInviteUseCase.cancel(currentUserId, clubId, inviteId)
                    call.respond(HttpStatusCode.NoContent)
                }

                get("/permissions") {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    call.respond(getClubPermissionsUseCase(currentUserId, clubId))
                }

                patch("/permissions") {
                    val clubId =
                        call.parameters["clubId"]
                            ?: return@patch call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<UpdateClubPermissionsRequest>()

                    updateClubPermissionsUseCase(currentUserId, clubId, request)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }

    // WebSocket route with token-query-param auth (outside the main authenticate block), mirroring
    // matchRoutes()'s /matches/{matchId}/live -- browser WebSockets can't set an Authorization header.
    route("/clubs/{clubId}/matches/live") {
        webSocket {
            val clubId = call.parameters["clubId"] ?: return@webSocket

            try {
                val currentUserId = call.request.queryParameters["token"]
                    ?.run { JwtConfig.verifier.verify(this).subject }
                    ?: run {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                        return@webSocket
                    }

                getClubMatchesUpdatesUseCase(currentUserId, clubId)
                    .collect(::sendSerialized)
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Forbidden or Not Found"))
            }
        }
    }
}
