package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.CreateAgeGroupUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.ageGroupRoutes() {
    val getAgeGroupsUseCase by inject<GetAgeGroupsUseCase>()
    val createAgeGroupUseCase by inject<CreateAgeGroupUseCase>()

    authenticate("auth-jwt") {
        route("/age-groups") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("clubId is required", "VAL_001"))
                val ageGroups = getAgeGroupsUseCase(clubId)
                call.respond(ageGroups)
            }

            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val ageGroupRequest = call.receive<AgeGroup>()
                val success = createAgeGroupUseCase(currentUserId, ageGroupRequest)
                if (success) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create age group", "-1"))
                }
            }
        }
    }
}
