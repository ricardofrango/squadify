package com.ricardofrango.squadify.data.service

import com.ricardofrango.squadify.domain.service.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Dev storage: one file per key under [baseDir] (`server/uploads/`, gitignored). */
class LocalImageStorage(private val baseDir: File) : ImageStorage {
    override val isAvailable: Boolean = true

    private fun fileFor(key: String): File {
        val file = File(baseDir, key).canonicalFile
        // Keys are built by the server, but never let one escape the folder.
        require(file.path.startsWith(baseDir.canonicalFile.path + File.separator)) { "Invalid image key" }
        return file
    }

    override suspend fun put(key: String, bytes: ByteArray, contentType: String) = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
    }

    override suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        fileFor(key).takeIf { it.isFile }?.readBytes()
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { fileFor(key).delete() }
    }
}
