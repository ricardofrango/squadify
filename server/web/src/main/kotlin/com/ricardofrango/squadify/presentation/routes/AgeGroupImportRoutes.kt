package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.ImportRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.domain.usecase.CreateExportGrantUseCase
import com.ricardofrango.squadify.domain.usecase.ExportAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.ImportAgeGroupUseCase
import com.ricardofrango.squadify.domain.usecase.ListExportableAgeGroupsUseCase
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroupBundle
import com.ricardofrango.squadify.model.ExportableAgeGroup
import com.ricardofrango.squadify.model.ImportableAgeGroup
import com.ricardofrango.squadify.model.ListSourceAgeGroupsRequest
import com.ricardofrango.squadify.model.ImportAgeGroupRequest
import com.ricardofrango.squadify.model.UserStatus
import com.ricardofrango.squadify.security.userId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
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
    val accessPolicy by inject<AccessPolicy>()
    val transactionManager by inject<TransactionManager>()
    val importRecords by inject<ImportRepository>()
    // The other server's replies are JSON: the client reads them into the bundle and the list.
    val client = HttpClient(CIO) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

    authenticate("auth-jwt") {
        route("/export/grants") {
            post {
                val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
                call.respond(createGrant(currentUserId))
            }
        }
        // The age groups a grant offers into a club, for the importing app's list: which of them are imported already.
        post("/import/source/age-groups") {
            val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<ListSourceAgeGroupsRequest>()
            val source = request.sourceUrl.trim().trimEnd('/')
            if (source !in sources.allowed) throw ValidationException("This server doesn't import from $source: set IMPORT_SOURCES on this server to that address")
            if (source in sources.crossEnvironment) requirePlatformAdmin(currentUserId, users, sources.platformAdminEmails)
            accessPolicy.requireEditClub(currentUserId, request.clubId, "Only club admins or coordinators can import an age group")
            val response = client.get("$source/v1/export/age-groups") { header(EXPORT_GRANT_HEADER, request.grant) }
            if (!response.status.isSuccess()) throw ValidationException("The other server refused the list (${response.status.value})")
            val offered: List<ExportableAgeGroup> = response.body()
            val linked = transactionManager.runInTransaction { importRecords.linkedAgeGroups(request.clubId, source) }
            call.respond(
                offered.map { a ->
                    ImportableAgeGroup(a.id, a.clubName, a.name, a.startYear, a.endYear, a.seasonName, alreadyImported = a.id in linked)
                }
            )
        }
        post("/import/age-groups") {
            val currentUserId = call.userId ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receive<ImportAgeGroupRequest>()
            val source = request.sourceUrl.trim().trimEnd('/')
            if (source !in sources.allowed) throw ValidationException("This server doesn't import from $source: set IMPORT_SOURCES on this server to that address")
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
