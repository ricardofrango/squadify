package com.ricardofrango.squadify.presentation.routes

import com.ricardofrango.squadify.domain.usecase.GetPublicClubLogoUseCase
import com.ricardofrango.squadify.domain.usecase.GetPublicOpponentLogoUseCase
import com.ricardofrango.squadify.domain.usecase.GetPublicPlayerPhotoUseCase
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

// The only images served without signing in, for push notifications (Android downloads a
// notification's image itself): a club's and an opponent's current logo, and -- behind a signed,
// expiring link -- a goal scorer's photo. See GetPublicClubLogoUseCase and PublicImageLinks.
fun Route.publicImageRoutes() {
    val getPublicClubLogoUseCase by inject<GetPublicClubLogoUseCase>()
    val getPublicOpponentLogoUseCase by inject<GetPublicOpponentLogoUseCase>()
    val getPublicPlayerPhotoUseCase by inject<GetPublicPlayerPhotoUseCase>()

    get("/public/clubs/{clubId}/logo/{file}") {
        val clubId = call.parameters["clubId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val bytes = getPublicClubLogoUseCase(clubId, imageId)
        // A new image id on every upload, so the file never changes under a path: cache it for good.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondBytes(bytes, ContentType.Image.PNG)
    }

    get("/public/opponents/{opponentId}/logo/{file}") {
        val opponentId = call.parameters["opponentId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val bytes = getPublicOpponentLogoUseCase(opponentId, imageId)
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondBytes(bytes, ContentType.Image.PNG)
    }

    get("/public/players/{playerId}/photo/{file}") {
        val playerId = call.parameters["playerId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val imageId = call.parameters["file"]?.removeSuffix(".png") ?: return@get call.respond(HttpStatusCode.BadRequest)
        val bytes = getPublicPlayerPhotoUseCase(
            playerId, imageId,
            expires = call.request.queryParameters["exp"]?.toLongOrNull(),
            sig = call.request.queryParameters["sig"]
        )
        // A minor's photo behind an expiring link: no shared caches.
        call.response.header(HttpHeaders.CacheControl, "private, max-age=86400")
        call.respondBytes(bytes, ContentType.Image.PNG)
    }
}
