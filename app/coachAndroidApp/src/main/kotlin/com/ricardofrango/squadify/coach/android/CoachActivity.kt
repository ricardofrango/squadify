package com.ricardofrango.squadify.coach.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import com.ricardofrango.squadify.coach.CoachApp

class CoachActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // The keyboard resizes the app (adjustResize), and its whole height is consumed here, as
            // in the clubs app's MainActivity.
            Box(Modifier.fillMaxSize().imePadding()) {
                CoachApp()
            }
        }
    }
}
