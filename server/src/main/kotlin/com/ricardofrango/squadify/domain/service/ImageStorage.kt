package com.ricardofrango.squadify.domain.service

/**
 * Where uploaded images (club logos) live, by key -- e.g. `clubs/{clubId}/logo/{imageId}.png`. A Railway
 * Storage Bucket in staging/production, the local disk in dev. See TECHNICAL_DECISIONS.md §56.
 */
interface ImageStorage {
    /** False when no storage is set up (production without a bucket): uploads are refused. */
    val isAvailable: Boolean

    suspend fun put(key: String, bytes: ByteArray, contentType: String)

    /** Null when there's nothing at [key]. */
    suspend fun get(key: String): ByteArray?

    /** Deleting a key that doesn't exist is fine. */
    suspend fun delete(key: String)
}
