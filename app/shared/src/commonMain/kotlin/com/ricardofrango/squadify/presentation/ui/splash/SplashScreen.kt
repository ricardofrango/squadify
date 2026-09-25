package com.ricardofrango.squadify.presentation.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.presentation.ui.components.SquadifyMark
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import squadify.app.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The static launch frame (design canvas "19A"): the "18a" formation mark over a flat carbon
 * ground, no spinner -- a launch screen that shows progress is admitting it's slow, and this is
 * meant to read as a still frame rather than a loading state. Session-check latency is handled by
 * [SplashViewModel] navigating away as soon as it resolves; there's nothing here to animate while
 * it waits.
 */
@Composable
fun SplashScreen(
    deepLinkRoute: String?,
    onNavigateToLogin: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToOnlyClub: (String) -> Unit,
    onNavigateToDeepLink: (String) -> Unit,
    viewModel: SplashViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.setDeepLinkRoute(deepLinkRoute)
        viewModel.navigationEvent.collect { event ->
            when (event) {
                SplashNavigationEvent.NavigateToLogin -> onNavigateToLogin()
                SplashNavigationEvent.NavigateToDashboard -> onNavigateToDashboard()
                is SplashNavigationEvent.NavigateToOnlyClub -> onNavigateToOnlyClub(event.clubId)
                is SplashNavigationEvent.NavigateToDeepLink -> onNavigateToDeepLink(event.route)
            }
        }
    }

    SplashContent()
}

@Composable
private fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCarbon),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SquadifyMark(size = 96.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

@Preview
@Composable
private fun SplashScreenPreview() {
    SquadifyTheme {
        SplashContent()
    }
}
