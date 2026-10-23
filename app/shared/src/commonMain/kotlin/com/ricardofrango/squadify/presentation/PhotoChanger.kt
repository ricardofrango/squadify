package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.presentation.ui.components.PhotoState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_logo_too_big
import squadify.app.shared.generated.resources.msg_photo_remove_failed
import squadify.app.shared.generated.resources.msg_photo_upload_failed

/**
 * Saves a photo straight away for a view model (Account, Edit player): one change at a time, the
 * new path or a localized error kept in a [PhotoState]. [read] and [write] reach the state, which
 * lives in the screen's own UI state.
 */
internal class PhotoChanger(
    private val scope: CoroutineScope,
    private val read: () -> PhotoState?,
    private val write: ((PhotoState) -> PhotoState) -> Unit
) {
    /** Uploads [bytes]; [onChanged] runs once it's saved. Too big for the server, it's refused here. */
    fun upload(bytes: ByteArray, upload: suspend (ByteArray) -> String, onChanged: () -> Unit = {}) {
        if (bytes.size > MAX_PHOTO_BYTES) {
            scope.launch {
                val message = getString(Res.string.msg_logo_too_big)
                write { it.copy(error = message) }
            }
            return
        }
        change(Res.string.msg_photo_upload_failed, onChanged) { upload(bytes) }
    }

    fun remove(delete: suspend () -> Unit, onChanged: () -> Unit = {}) =
        change(Res.string.msg_photo_remove_failed, onChanged) { delete(); null }

    private fun change(failure: StringResource, onChanged: () -> Unit, action: suspend () -> String?) {
        if (read()?.isUpdating != false) return
        scope.launch {
            write { it.copy(isUpdating = true, error = null) }
            try {
                val url = action()
                write { it.copy(url = url, isUpdating = false) }
                onChanged()
            } catch (e: Exception) {
                val message = errorMessage(e, failure)
                write { it.copy(isUpdating = false, error = message) }
            }
        }
    }
}

// The server's limit (LogoImage.MAX_UPLOAD_BYTES): checked here too, so a big photo isn't sent for nothing.
internal const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
