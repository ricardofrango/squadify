package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.data.remote.ApiException
import io.ktor.http.HttpStatusCode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_permission_denied

/**
 * Maps a caught exception to localized, user-facing copy (AI_GUIDELINES.md "No Raw Exception
 * Messages in UI"). A 403 from the server always becomes the same specific "you don't have
 * permission" message regardless of which action failed -- that's a far more useful signal than the
 * caller's generic fallback, and it's what actually happened: the server's `ForbiddenException`
 * message itself is deliberately never shown (it can describe internal role/permission plumbing).
 * Every other failure uses the caller's own fallback copy, unchanged.
 */
suspend fun errorMessage(e: Throwable, fallback: StringResource): String =
    if (e is ApiException && e.statusCode == HttpStatusCode.Forbidden.value) {
        getString(Res.string.msg_permission_denied)
    } else {
        getString(fallback)
    }
