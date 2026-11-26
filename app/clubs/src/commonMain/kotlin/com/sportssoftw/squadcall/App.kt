package com.sportssoftw.squadcall

import androidx.compose.runtime.Composable
import com.sportssoftw.squadcall.presentation.ui.startup.FeatureFlagsGate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.sportssoftw.squadcall.presentation.navigation.AppNavigation
import com.sportssoftw.squadcall.presentation.navigation.LocalAppNavigator
import com.sportssoftw.squadcall.presentation.navigation.rememberAppNavigator
import com.sportssoftw.squadcall.presentation.navigation.LocalClubThemes
import com.sportssoftw.squadcall.presentation.ui.theme.ClubThemeStore
import com.sportssoftw.squadcall.presentation.ui.theme.SquadcallTheme
import io.ktor.client.HttpClient
import org.koin.compose.koinInject

@Composable
fun App() {
    // Images (club logos) are served to signed-in members only, so Coil fetches them with the same
    // authenticated client as the API (bearer token, refresh on 401).
    val httpClient = koinInject<HttpClient>()
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { httpClient })) }
            .build()
    }
    SquadcallTheme {
        // A club's screens find their club (SquadcallClubTheme) through these.
        val navigator = rememberAppNavigator()
        val clubThemes by koinInject<ClubThemeStore>().themes.collectAsStateWithLifecycle()
        CompositionLocalProvider(LocalAppNavigator provides navigator, LocalClubThemes provides clubThemes) {
            // Nothing opens until the server's feature flags have been read (TECHNICAL_DECISIONS §82).
            FeatureFlagsGate { AppNavigation(navigator) }
        }
    }
}
