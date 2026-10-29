package com.ricardofrango.squadify.domain.usecase

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The public addresses of the images a goal push shows on its right (Android downloads a notification's
 * image itself, without signing in). See TECHNICAL_DECISIONS.md §57.
 *
 * - **An opponent's logo** is served like the club's own logo: to anyone, but only under its current
 *   random image id.
 * - **A player's photo** is a minor's, readable only by the club's members, who are also the only ones
 *   a goal push goes to. So its link is signed and runs out after [photoLinkLifetime]: it can't be
 *   guessed, and a forwarded or logged one stops working.
 *
 * Null when the server has no public address, or the image isn't one of ours.
 */
class PublicImageLinks(
    private val publicBaseUrl: String?,
    secret: String,
    private val clock: Clock = Clock.System,
    private val photoLinkLifetime: Duration = 24.hours
) {
    // Its own key, derived from the server secret, so a photo signature is never valid for anything else.
    private val key = hmac("squadify-public-player-photo".toByteArray(), secret.toByteArray())

    fun opponentLogo(opponentId: String, logoUrl: String?): String? {
        if (publicBaseUrl == null || logoUrl == null || OpponentLogos.keyOf(opponentId, logoUrl) == null) return null
        return "$publicBaseUrl/v1/public$logoUrl"
    }

    fun playerPhoto(playerId: String, photoUrl: String?): String? {
        if (publicBaseUrl == null || photoUrl == null || PlayerPhotos.keyOf(playerId, photoUrl) == null) return null
        val expires = (clock.now() + photoLinkLifetime).epochSeconds
        return "$publicBaseUrl/v1/public$photoUrl?exp=$expires&sig=${signature(photoUrl, expires)}"
    }

    /** Whether [sig] is ours for this photo and [expires] hasn't passed. */
    fun isValidPhotoLink(playerId: String, imageId: String, expires: Long, sig: String): Boolean {
        if (clock.now().epochSeconds > expires) return false
        val expected = signature(PlayerPhotos.url(playerId, imageId), expires)
        return java.security.MessageDigest.isEqual(expected.toByteArray(), sig.toByteArray())
    }

    private fun signature(photoUrl: String, expires: Long): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(hmac(key, "$photoUrl|$expires".toByteArray()))

    private fun hmac(key: ByteArray, message: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)
}
