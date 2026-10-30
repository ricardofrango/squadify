package com.ricardofrango.squadify.config

import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The local Firebase credential sits at the repo root while Gradle runs the server from its module
// folder, however deep that is (server/clubs today).
class FindUpwardsTest {
    @Test
    fun theFileIsFoundInAFolderAboveTheStart() {
        val root = createTempDirectory("repo").toFile()
        val credential = File(root, "firebase-service-account.json").apply { writeText("{}") }
        val module = File(root, "server/clubs").apply { mkdirs() }

        assertEquals(credential.canonicalFile, findUpwards(module, "firebase-service-account.json")?.canonicalFile)
        root.deleteRecursively()
    }

    @Test
    fun theNearestOneWins() {
        val root = createTempDirectory("repo").toFile()
        File(root, "firebase-service-account.json").writeText("{}")
        val module = File(root, "server").apply { mkdirs() }
        val nearer = File(module, "firebase-service-account.json").apply { writeText("{}") }

        assertEquals(nearer.canonicalFile, findUpwards(File(module, "clubs").apply { mkdirs() }, "firebase-service-account.json")?.canonicalFile)
        root.deleteRecursively()
    }

    @Test
    fun noFileAnywhereIsNull() {
        val root = createTempDirectory("repo").toFile()
        assertNull(findUpwards(root, "no-such-file-${System.nanoTime()}.json"))
        root.deleteRecursively()
    }
}
