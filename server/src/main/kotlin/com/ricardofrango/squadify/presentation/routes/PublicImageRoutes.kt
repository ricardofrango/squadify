package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.GetPublicClubLogoUseCase
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

// The only images served without signing in: a club's current logo, for its push notifications
// (Android downloads a notification's image itself). See GetPublicClubLogoUseCase.
fun Route.publicImageRoutes() {
    val getPublicClubLogoUseCase by inject<GetPublicClubLogoUseCase>()

    get("/public/clubs/{clubId}/logo/{file}") {
        val clubId = call.parameters["clubId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val bytes = getPublicClubLogoUseCase(clubId, imageId)
        // A new image id on every upload, so the file never changes under a path: cache it for good.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondBytes(bytes, ContentType.Image.PNG)
    }
}
