package com.ricardofrango.squadify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ricardofrango.squadify.presentation.navigation.PushLinkInbox
import com.ricardofrango.squadify.push.PushNotifications
import com.ricardofrango.squadify.push.RequestNotificationPermission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainActivity : ComponentActivity(), KoinComponent {

    private val pushLinkInbox: PushLinkInbox by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not after a rotation or process restore: the intent is the same one, already opened.
        if (savedInstanceState == null) PushNotifications.takePushLink(intent, pushLinkInbox)

        setContent {
            RequestNotificationPermission()
            // The keyboard resizes the app instead of sliding the window up (adjustResize in the
            // manifest; without it Android panned the whole window, pushing the top bar under the status
            // bar and re-panning on every new field): everything is laid out above the keyboard. The
            // full keyboard height: it covers the navigation bar, and consuming it here means a screen's
            // navigationBarsPadding() and imePadding() add nothing more while it's open (consumed insets
            // are counted per side, not per type, so leaving the navigation bar out here left pinned bars
            // a navigation bar's height under the keyboard).
            Box(Modifier.fillMaxSize().imePadding()) {
                App()
            }
        }
    }

    // singleTop: a notification tapped while the app is open comes here instead of a second activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PushNotifications.takePushLink(intent, pushLinkInbox)
    }

}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}