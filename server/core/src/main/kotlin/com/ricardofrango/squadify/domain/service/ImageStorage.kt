package com.ricardofrango.squadify.domain.service

/**
 * Where uploaded images (logos and photos) live, by key -- e.g. `clubs/{clubId}/logo/{imageId}.png`. A
 * Railway Storage Bucket in staging/production, the local disk in dev. See TECHNICAL_DECISIONS.md §57.
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

/**
 * [storage] with every key under [prefix] (e.g. `coach/`), so two servers can share one bucket without
 * their files mixing. Only the storage key changes: the image's path under the API stays the same.
 */
class PrefixedImageStorage(private val storage: ImageStorage, private val prefix: String) : ImageStorage {
    override val isAvailable: Boolean get() = storage.isAvailable
    override suspend fun put(key: String, bytes: ByteArray, contentType: String) = storage.put(prefix + key, bytes, contentType)
    override suspend fun get(key: String): ByteArray? = storage.get(prefix + key)
    override suspend fun delete(key: String) = storage.delete(prefix + key)
}
