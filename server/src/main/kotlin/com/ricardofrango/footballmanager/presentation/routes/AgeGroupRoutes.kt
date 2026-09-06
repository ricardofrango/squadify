package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.AddPlayersToAgeGroupUseCase
import com.ricardofrango.footballmanager.domain.usecase.AddStaffToAgeGroupUseCase
import com.ricardofrango.footballmanager.domain.usecase.CreateAgeGroupsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetAgeGroupStaffUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetEligiblePlayersForAgeGroupUseCase
import com.ricardofrango.footballmanager.domain.usecase.UpdateAgeGroupUseCase
import com.ricardofrango.footballmanager.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.footballmanager.model.AddStaffToAgeGroupRequest
import com.ricardofrango.footballmanager.model.CreateAgeGroupsRequest
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.model.UpdateAgeGroupRequest
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.ageGroupRoutes() {
    val getAgeGroupsUseCase by inject<GetAgeGroupsUseCase>()
    val createAgeGroupsUseCase by inject<CreateAgeGroupsUseCase>()
    val updateAgeGroupUseCase by inject<UpdateAgeGroupUseCase>()
    val addPlayersToAgeGroupUseCase by inject<AddPlayersToAgeGroupUseCase>()
    val getEligiblePlayersForAgeGroupUseCase by inject<GetEligiblePlayersForAgeGroupUseCase>()
    val addStaffToAgeGroupUseCase by inject<AddStaffToAgeGroupUseCase>()
    val getAgeGroupStaffUseCase by inject<GetAgeGroupStaffUseCase>()

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

            route("/{ageGroupId}/players") {
                // Players eligible to be added (club players old enough, not already in the group).
                get("/eligible") {
                    val ageGroupId = call.parameters["ageGroupId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val currentUserId =
                        call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    call.respond(getEligiblePlayersForAgeGroupUseCase(currentUserId, ageGroupId))
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
