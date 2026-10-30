package com.ricardofrango.squadify.coach.presentation.share

import androidx.compose.runtime.Composable

/** Sending text out of the app: the invite codes (TECHNICAL_DECISIONS §72). */
interface TextSharing {
    /** The phone's share menu (WhatsApp, SMS, email apps...) with [text]. */
    fun share(text: String)

    /** [text] on the clipboard. */
    fun copy(text: String)
}

@Composable
expect fun rememberTextSharing(): TextSharing
