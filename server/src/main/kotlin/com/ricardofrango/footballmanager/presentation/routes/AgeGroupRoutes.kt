package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.CreateAgeGroupUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetAgeGroupsUseCase
import com.ricardofrango.footballmanager.model.CreateAgeGroupRequest
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.ageGroupRoutes() {
    val getAgeGroupsUseCase by inject<GetAgeGroupsUseCase>()
    val createAgeGroupUseCase by inject<CreateAgeGroupUseCase>()

    authenticate("auth-jwt") {
        route("/age-groups") {
            get {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val ageGroups = getAgeGroupsUseCase(currentUserId, clubId)
                call.respond(ageGroups)
            }

            post {
                val currentUserId =
                    call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val ageGroupRequest = call.receive<CreateAgeGroupRequest>()
                val ageGroup = createAgeGroupUseCase(currentUserId, ageGroupRequest)
                call.respond(HttpStatusCode.Created, ageGroup)
            }
        }
    }
}
