package com.ricardofrango.squadify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.ricardofrango.squadify.model.PushLink
import com.ricardofrango.squadify.presentation.navigation.PushLinkInbox
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainActivity : ComponentActivity(), KoinComponent {

    private val pushLinkInbox: PushLinkInbox by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not after a rotation or process restore: the intent is the same one, already opened.
        if (savedInstanceState == null) takePushLink(intent)

        setContent {
            RequestNotificationPermission()
            // The keyboard resizes the app instead of sliding the window up (adjustResize in the
            // manifest; without it Android panned the whole window, pushing the top bar under the status
            // bar and re-panning on every new field): everything is laid out above the keyboard. The
            // navigation bar is left out, since each screen's Scaffold already pads for it; the insets
            // used here are consumed, so a screen's own imePadding() doesn't add them again.
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))) {
                App()
            }
        }
    }

    // singleTop: a notification tapped while the app is open comes here instead of a second activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takePushLink(intent)
    }

    /**
     * A tapped push notification's link (§58), from the intent's extras: Android puts the message's
     * data there when it shows the notification itself (app in the background), and
     * SquadifyMessagingService copies it there when the app shows it. Removed afterwards so the same
     * intent isn't opened twice.
     */
    private fun takePushLink(intent: Intent?) {
        val extras = intent?.extras ?: return
        val link = PushLink.fromData(PushLink.KEYS.associateWith { extras.getString(it) }) ?: return
        PushLink.KEYS.forEach(intent::removeExtra)
        pushLinkInbox.post(link)
    }
}

/**
 * POST_NOTIFICATIONS is a runtime permission on API 33+ (declaring it in the manifest alone doesn't
 * grant it) -- without this, FCM registration and delivery still work exactly the same, but
 * SquadifyMessagingService's notify() call is a silent no-op (see its areNotificationsEnabled() guard),
 * so nothing ever actually shows. Requested once per app launch; the system itself remembers a prior
 * grant/denial and won't re-prompt if the user already decided.
 */
@Composable
private fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}