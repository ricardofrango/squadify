package com.ricardofrango.squadify.presentation.ui.splash

import com.ricardofrango.squadify.presentation.navigation.Screen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.components.SquadifyMark
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * The session check at launch ([SplashViewModel], navigating away as soon as it resolves), drawn as the
 * platform's own launch splash so the two read as one: the mark alone in the middle of the carbon, no
 * name, no spinner. On Android it's never seen: the activity keeps the system splash on screen until
 * this screen has left ([LaunchSplash]). On iOS it takes over from the launch screen (`LaunchMark`, the
 * same size), and on the web and desktop it's the only one.
 */
@Composable
fun SplashScreen(
    deepLinkRoute: String?,
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: (Screen) -> Unit,
    onNavigateToDeepLink: (String) -> Unit,
    onNavigateToBackStack: (List<Screen>) -> Unit,
    viewModel: SplashViewModel = koinViewModel()
) {
    // Gone (the navigation's transition done): the system splash can let go.
    DisposableEffect(Unit) {
        onDispose { LaunchSplash.finish() }
    }
    LaunchedEffect(Unit) {
        viewModel.setDeepLinkRoute(deepLinkRoute)
        viewModel.navigationEvent.collect { event ->
            when (event) {
                SplashNavigationEvent.NavigateToLogin -> onNavigateToLogin()
                is SplashNavigationEvent.NavigateToHome -> onNavigateToHome(event.screen)
                is SplashNavigationEvent.NavigateToDeepLink -> onNavigateToDeepLink(event.route)
                is SplashNavigationEvent.NavigateToBackStack -> onNavigateToBackStack(event.screens)
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
        // The launch splash's mark: 87 wide, as on iOS's launch screen (the dots span 71% of the size);
        // the clubs app's disc then matches its launch screen's too.
        SquadifyMark(size = 122.dp)
    }
}

@Preview
@Composable
private fun SplashScreenPreview() {
    SquadifyTheme {
        SplashContent()
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun SplashScreenLandscapePreview() {
    SquadifyTheme {
        SplashContent()
    }
}
