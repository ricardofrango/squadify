package com.ricardofrango.squadify.presentation.credentials

import androidx.compose.runtime.Composable

// No password manager to talk to here; the screens fall back to autofill hints.
@Composable
actual fun rememberCredentialStore(): CredentialStore = NoCredentialStore
