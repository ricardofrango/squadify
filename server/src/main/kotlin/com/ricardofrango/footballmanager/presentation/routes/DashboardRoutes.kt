package com.ricardofrango.footballmanager.presentation.routes

import com.ricardofrango.footballmanager.domain.usecase.GetClubAdminDashboardUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetParentDashboardUseCase
import com.ricardofrango.footballmanager.domain.usecase.GetSuperAdminDashboardUseCase
import com.ricardofrango.footballmanager.model.ErrorResponse
import com.ricardofrango.footballmanager.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.dashboardRoutes() {
    val getSuperAdminDashboardUseCase by inject<GetSuperAdminDashboardUseCase>()
    val getClubAdminDashboardUseCase by inject<GetClubAdminDashboardUseCase>()
    val getParentDashboardUseCase by inject<GetParentDashboardUseCase>()

    authenticate("auth-jwt") {
        route("/dashboards") {
            get("/super-admin") {
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val dashboard = getSuperAdminDashboardUseCase(currentUserId)
                call.respond(dashboard)
            }

            get("/club-admin") {
                val clubId = call.request.queryParameters["clubId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("clubId is required", "VAL_001")
                )
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val dashboard = getClubAdminDashboardUseCase(currentUserId, clubId)
                call.respond(dashboard)
            }

            get("/parent") {
                val currentUserId =
                    call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val dashboard = getParentDashboardUseCase(currentUserId)
                call.respond(dashboard)
            }
        }
    }
}
