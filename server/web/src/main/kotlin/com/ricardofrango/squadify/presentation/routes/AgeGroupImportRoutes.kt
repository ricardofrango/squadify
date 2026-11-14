package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.usecase.CreateExportGrantUseCase
import com.ricardofrango.squadify.domain.usecase.ExportAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.ImportAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.ListExportableAgeGroupsUseCase
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroupBundle
import com.ricardofrango.squadify.model.ImportAgeGroupRequest
import com.ricardofrango.squadify.model.UserStatus
import com.ricardofrango.squadify.security.userId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Which other servers an age group may be imported from (TECHNICAL_DECISIONS §81): [allowed] by their address; those
 * in [crossEnvironment] (another environment, e.g. staging into production) only for a platform admin.
 */
data class ImportSources(val allowed: Set<String>, val crossEnvironment: Set<String>, val platformAdminEmails: Set<String>)

/**
 * The age group import on this server (TECHNICAL_DECISIONS §81). Export, read by the other server with a grant
 * (no sign-in: the grant is the permission): the grant itself, the list of age groups it may read, and one bundle,
 * which uses the grant up. Import, by a signed-in user into a club they may edit: the source's bundle is fetched with
 * the grant and written in one transaction.
 */
fun Route.ageGroupImportRoutes(sources: ImportSources) {
    val createGrant by inject<CreateExportGrantUseCase>()
    val listExportable by inject<ListExportableAgeGroupsUseCase>()
    val exportBundle by inject<ExportAgeGroupUseCase>()
    val importAgeGroup by inject<ImportAgeGroupUseCase>()
    val users by inject<UserRepository>()
    val client = HttpClient(CIO)

    authenticate("auth-jwt") {
        route("/export/grants") {
            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                call.respond(createGrant(currentUserId))
            }
        }
        post("/import/age-groups") {
            val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<ImportAgeGroupRequest>()
            val source = request.sourceUrl.trim().trimEnd('/')
            if (source !in sources.allowed) throw ValidationException("This server doesn't import from that address")
            if (source in sources.crossEnvironment) requirePlatformAdmin(currentUserId, users, sources.platformAdminEmails)
            val response = client.get("$source/v1/export/age-groups/${request.sourceAgeGroupId}/bundle") {
                header(EXPORT_GRANT_HEADER, request.grant)
            }
            if (!response.status.isSuccess()) throw ValidationException("The other server refused the import (${response.status.value})")
            val bundle: AgeGroupBundle = response.body()
            call.respond(HttpStatusCode.Created, importAgeGroup(currentUserId, request.clubId, source, bundle))
        }
    }

    // Read by the other server with the grant, not by a signed-in user.
    get("/export/age-groups") {
        val grant = call.request.headers[EXPORT_GRANT_HEADER] ?: return@get call.respond(HttpStatusCode.Unauthorized)
        call.respond(listExportable(grant))
    }
    get("/export/age-groups/{ageGroupId}/bundle") {
        val grant = call.request.headers[EXPORT_GRANT_HEADER] ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val ageGroupId = call.parameters["ageGroupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        call.respond(exportBundle(grant, ageGroupId))
    }
}

private const val EXPORT_GRANT_HEADER = "X-Export-Grant"

private suspend fun requirePlatformAdmin(userId: String, users: UserRepository, adminEmails: Set<String>) {
    val user = users.findById(userId)
    val admin = user != null && user.status == UserStatus.ACTIVE && (user.isPlatformAdmin || user.email.lowercase() in adminEmails)
    if (!admin) throw ForbiddenException("Only a platform admin can import from another environment")
}
