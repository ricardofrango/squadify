package com.ricardofrango.squadify.coach.presentation.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberTextSharing(): TextSharing {
    val context = LocalContext.current
    return remember(context) {
        object : TextSharing {
            override fun share(text: String) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, null))
            }

            override fun copy(text: String) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
            }
        }
    }
}
