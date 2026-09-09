package com.ricardofrango.footballmanager.presentation.ui.livematch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ricardofrango.footballmanager.presentation.ui.components.FMTopBar
import com.ricardofrango.footballmanager.presentation.ui.theme.DeepCarbon
import footballmanager.app.shared.generated.resources.Res
import footballmanager.app.shared.generated.resources.msg_live_match_coming_soon
import footballmanager.app.shared.generated.resources.title_live_match
import org.jetbrains.compose.resources.stringResource

/**
 * Placeholder for the live match screen -- the "Start match" / "Enter live match" button on the
 * match screen routes here. Starting the game, substitutions, goals and cards will live here.
 */
@Composable
fun LiveMatchScreen(
    @Suppress("UNUSED_PARAMETER") matchId: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = Color.White,
        topBar = { FMTopBar(title = stringResource(Res.string.title_live_match), onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.msg_live_match_coming_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = DeepCarbon.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
