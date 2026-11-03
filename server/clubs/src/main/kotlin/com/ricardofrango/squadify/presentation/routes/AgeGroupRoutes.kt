package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.AddPlayersToAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.RemovePlayerFromAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.AddStaffToAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.CreateAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupStaffUseCase
import com.ricardofrango.squadify.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.squadify.domain.usecase.GetEligiblePlayersForAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.UpdateAgeGroupUseCase
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AddStaffToAgeGroupRequest
import com.ricardofrango.squadify.model.CreateAgeGroupsRequest
import com.ricardofrango.squadify.model.ErrorResponse
import com.ricardofrango.squadify.model.Paging
import com.ricardofrango.squadify.model.UpdateAgeGroupRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.ageGroupRoutes() {
    val getAgeGroupsUseCase by inject<GetAgeGroupsUseCase>()
    val createAgeGroupsUseCase by inject<CreateAgeGroupsUseCase>()
    val updateAgeGroupUseCase by inject<UpdateAgeGroupUseCase>()
    val addPlayersToAgeGroupUseCase by inject<AddPlayersToAgeGroupUseCase>()
    val removePlayerFromAgeGroupUseCase by inject<RemovePlayerFromAgeGroupUseCase>()
    val getEligiblePlayersForAgeGroupUseCase by inject<GetEligiblePlayersForAgeGroupUseCase>()
    val addStaffToAgeGroupUseCase by inject<AddStaffToAgeGroupUseCase>()
    val getAgeGroupStaffUseCase by inject<GetAgeGroupStaffUseCase>()
    val getAgeGroupDetailsUseCase by inject<GetAgeGroupDetailsUseCase>()

    authenticate("auth-jwt") {
        route("/age-groups") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val ageGroups = getAgeGroupsUseCase(currentUserId, clubId)
                call.respond(ageGroups)
            }

            post {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<CreateAgeGroupsRequest>()
                val ageGroups = createAgeGroupsUseCase(currentUserId, request)
                call.respond(HttpStatusCode.Created, ageGroups)
            }

            patch("/{ageGroupId}") {
                val ageGroupId = call.parameters["ageGroupId"] ?: return@patch call.respond(
                    HttpStatusCode.BadRequest
                )
                val currentUserId =
                    call.userId ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<UpdateAgeGroupRequest>()

                val success = updateAgeGroupUseCase(currentUserId, ageGroupId, request)
                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to update age group")
                    )
                }
            }

            // The season record and full stats are shared game routes (gameRoutes, :server:web).

            get("/{ageGroupId}/details") {
                val ageGroupId = call.parameters["ageGroupId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getAgeGroupDetailsUseCase(currentUserId, ageGroupId))
            }

            route("/{ageGroupId}/players") {
                // Players eligible to be added (club players old enough, not already in the group). With
                // `limit`, a page of them (`offset`) by name, searched on `q`; without, all of them.
                get("/eligible") {
                    val ageGroupId = call.parameters["ageGroupId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val params = call.request.queryParameters
                    val limit = Paging.limitOf(params["limit"])
                    if (limit == null) {
                        call.respond(getEligiblePlayersForAgeGroupUseCase(currentUserId, ageGroupId))
                    } else {
                        call.respond(
                            getEligiblePlayersForAgeGroupUseCase.page(currentUserId, ageGroupId, params["q"], limit, Paging.offsetOf(params["offset"]))
                        )
                    }
                }

                post {
                    val ageGroupId = call.parameters["ageGroupId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<AddPlayersToAgeGroupRequest>()
                    call.respond(
                        HttpStatusCode.Created,
                        addPlayersToAgeGroupUseCase(currentUserId, ageGroupId, request)
                    )
                }

                // Out of this age group's pool, not out of the club (RemovePlayerFromAgeGroupUseCase).
                delete("/{playerId}") {
                    val ageGroupId = call.parameters["ageGroupId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val playerId = call.parameters["playerId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val currentUserId = call.userId ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                    removePlayerFromAgeGroupUseCase(currentUserId, ageGroupId, playerId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/{ageGroupId}/staff") {
                get {
                    val ageGroupId = call.parameters["ageGroupId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    call.respond(getAgeGroupStaffUseCase(currentUserId, ageGroupId))
                }

                post {
                    val ageGroupId = call.parameters["ageGroupId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                    val request = call.receive<AddStaffToAgeGroupRequest>()
                    val success = addStaffToAgeGroupUseCase(currentUserId, ageGroupId, request)
                    if (success) {
                        call.respond(HttpStatusCode.Created)
                    } else {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Failed to add staff")
                        )
                    }
                }
            }
        }
    }
}
