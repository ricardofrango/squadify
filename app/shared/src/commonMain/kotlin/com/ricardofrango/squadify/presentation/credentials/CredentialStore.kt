package com.ricardofrango.squadify.presentation.credentials

import androidx.compose.runtime.Composable

/** An account saved in the platform's password manager. */
data class SavedCredential(val email: String, val password: String)

/**
 * The platform's password manager: Android's Credential Manager, nothing elsewhere ([isAvailable]
 * false, where the screens fall back to autofill). Neither call ever throws.
 */
interface CredentialStore {
    val isAvailable: Boolean

    /** Offers to save [email] and [password] -- the system's "save password?" sheet. */
    suspend fun save(email: String, password: String)

    /** An account the user picks from their saved ones; null when there's none or they dismiss it. */
    suspend fun pick(): SavedCredential?
}

/** Where there's no password manager to talk to. */
object NoCredentialStore : CredentialStore {
    override val isAvailable = false
    override suspend fun save(email: String, password: String) {}
    override suspend fun pick(): SavedCredential? = null
}

/** This platform's [CredentialStore], for the current screen. */
@Composable
expect fun rememberCredentialStore(): CredentialStore
