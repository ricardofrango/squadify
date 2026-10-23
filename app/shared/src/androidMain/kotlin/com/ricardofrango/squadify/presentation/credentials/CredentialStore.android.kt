package com.ricardofrango.squadify.presentation.credentials

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun rememberCredentialStore(): CredentialStore {
    // The screen's context is the Activity, which Credential Manager needs to show its sheets.
    val context = LocalContext.current
    return remember(context) { AndroidCredentialStore(context) }
}

/** Jetpack Credential Manager: saved passwords (Google Password Manager or the user's own provider). */
private class AndroidCredentialStore(private val context: Context) : CredentialStore {
    private val manager = CredentialManager.create(context)

    override val isAvailable = true

    override suspend fun save(email: String, password: String) {
        try {
            manager.createCredential(context, CreatePasswordRequest(email, password))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Dismissed, or no provider: nothing to do.
        }
    }

    override suspend fun pick(): SavedCredential? = try {
        // Only shows a sheet when there's something to pick: with nothing saved it fails quietly
        // instead of showing an empty "no accounts" sheet on every visit to Login.
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetPasswordOption())
            .setPreferImmediatelyAvailableCredentials(true)
            .build()
        (manager.getCredential(context, request).credential as? PasswordCredential)
            ?.let { SavedCredential(it.id, it.password) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
