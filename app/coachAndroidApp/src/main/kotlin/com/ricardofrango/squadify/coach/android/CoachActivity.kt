package com.ricardofrango.squadify.coach.android

import android.content.Intent
import com.ricardofrango.squadify.presentation.navigation.PushLinkInbox
import com.ricardofrango.squadify.push.PushNotifications
import com.ricardofrango.squadify.push.RequestAppPermissions
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import com.ricardofrango.squadify.coach.CoachApp

class CoachActivity : ComponentActivity(), KoinComponent {

    private val pushLinkInbox: PushLinkInbox by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A tapped notification's link; not after a rotation or process restore (the same intent).
        if (savedInstanceState == null) PushNotifications.takePushLink(intent, pushLinkInbox)
        setContent {
            RequestAppPermissions()
            // The keyboard resizes the app (adjustResize), and its whole height is consumed here, as
            // in the clubs app's MainActivity.
            Box(Modifier.fillMaxSize().imePadding()) {
                CoachApp()
            }
        }
    }

    // A notification tapped while the app is open comes here (singleTop), not as a second activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        PushNotifications.takePushLink(intent, pushLinkInbox)
    }
}
