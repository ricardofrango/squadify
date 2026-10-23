package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.components.PhotoState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Account and Edit player save a photo straight away (PhotoChanger): the new path once it's saved,
// one change at a time, and nothing sent when the image is over the server's 5 MB.
class PhotoChangerTest {

    @Test
    fun anUploadShowsSavingThenTheNewPhoto() = runTest {
        var photo = PhotoState(url = "/players/p/photo/old.png")
        val changer = PhotoChanger(backgroundScope, { photo }, { photo = it(photo) })
        val done = CompletableDeferred<String>()
        var changed = 0

        changer.upload(byteArrayOf(1), { done.await() }, onChanged = { changed++ })
        runCurrent()
        assertTrue(photo.isUpdating)

        done.complete("/players/p/photo/new.png")
        runCurrent()
        assertEquals(PhotoState(url = "/players/p/photo/new.png"), photo)
        assertEquals(1, changed)
    }

    @Test
    fun aSecondChangeWhileOneIsSavingIsIgnored() = runTest {
        var photo = PhotoState()
        val changer = PhotoChanger(backgroundScope, { photo }, { photo = it(photo) })
        val first = CompletableDeferred<String>()
        var calls = 0

        changer.upload(byteArrayOf(1), { calls++; first.await() })
        runCurrent()
        changer.remove({ calls++ })
        runCurrent()

        assertEquals(1, calls)
        first.complete("/users/u/avatar/a.png")
        runCurrent()
        assertEquals("/users/u/avatar/a.png", photo.url)
    }

    @Test
    fun removingClearsThePhoto() = runTest {
        var photo = PhotoState(url = "/users/u/avatar/a.png")
        val changer = PhotoChanger(backgroundScope, { photo }, { photo = it(photo) })
        changer.remove({ })
        runCurrent()
        assertNull(photo.url)
        assertFalse(photo.isUpdating)
    }

    @Test
    fun anImageOverTheLimitIsNeverSent() {
        var photo = PhotoState()
        // A scheduler that's never run: the refusal's message needs the app's resources, which a JVM
        // test can't load, but nothing is sent before that point anyway.
        val changer = PhotoChanger(CoroutineScope(StandardTestDispatcher()), { photo }, { photo = it(photo) })
        var sent = false
        changer.upload(ByteArray(MAX_PHOTO_BYTES + 1), { sent = true; "x" })
        assertFalse(sent)
        assertFalse(photo.isUpdating)
    }
}
