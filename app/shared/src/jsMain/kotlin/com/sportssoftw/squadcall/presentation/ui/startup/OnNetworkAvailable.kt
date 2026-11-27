package com.sportssoftw.squadcall.presentation.ui.startup

import androidx.compose.runtime.Composable

/** No connection signal on this platform: the gate retries on Retry and when the app comes back to the front. */
@Composable
actual fun OnNetworkAvailable(onAvailable: () -> Unit) {
}
