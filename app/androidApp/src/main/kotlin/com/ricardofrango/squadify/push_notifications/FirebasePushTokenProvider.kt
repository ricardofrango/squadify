package com.ricardofrango.squadify.push_notifications

import com.google.firebase.messaging.FirebaseMessaging
import com.ricardofrango.squadify.domain.service.PushTokenProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps `FirebaseMessaging.getInstance().token` (a Play Services `Task`) as a suspend function --
 * a manual `suspendCancellableCoroutine` rather than pulling in the whole `kotlinx-coroutines-play-
 * services` artifact for one call.
 */
class FirebasePushTokenProvider : PushTokenProvider {
    override suspend fun getCurrentToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> continuation.resume(token) }
            .addOnFailureListener { continuation.resume(null) }
    }
}