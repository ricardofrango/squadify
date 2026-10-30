package com.ricardofrango.squadify.coach.routes

import com.ricardofrango.squadify.coach.domain.DeleteAccountUseCase
import com.ricardofrango.squadify.coach.domain.GetAccountDeletionSummaryUseCase
import com.ricardofrango.squadify.model.DeleteAccountRequest
import com.ricardofrango.squadify.security.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/** Deleting the caller's own account (the coach server only, for now): what it takes, then doing it. */
fun Route.accountDeletionRoutes() {
    val getSummary by inject<GetAccountDeletionSummaryUseCase>()
    val deleteAccount by inject<DeleteAccountUseCase>()

    authenticate("auth-jwt") {
        route("/users/me") {
            get("/deletion") {
                val currentUserId = call.userId ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(getSummary(currentUserId))
            }
            post("/delete") {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                deleteAccount(currentUserId, call.receive<DeleteAccountRequest>())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
