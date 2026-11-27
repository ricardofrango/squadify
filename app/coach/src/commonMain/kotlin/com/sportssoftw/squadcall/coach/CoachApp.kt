package com.sportssoftw.squadcall.coach

import com.sportssoftw.squadcall.coach.presentation.ads.AdsConsentEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.sportssoftw.squadcall.coach.presentation.navigation.CoachNavigation
import com.sportssoftw.squadcall.presentation.ui.startup.FeatureFlagsGate
import com.sportssoftw.squadcall.presentation.navigation.LocalAppNavigator
import com.sportssoftw.squadcall.presentation.navigation.rememberAppNavigator
import com.sportssoftw.squadcall.presentation.ui.components.LocalMarkOnTile
import com.sportssoftw.squadcall.presentation.ui.theme.SquadcallTheme
import io.ktor.client.HttpClient
import org.koin.compose.koinInject

/** Squadcall for Coaches: every platform's coach entry point shows this. */
@Composable
fun CoachApp() {
    // Images are served to signed-in members only, so Coil fetches them with the API's authenticated
    // client, as in the clubs app.
    val httpClient = koinInject<HttpClient>()
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { httpClient })) }
            .build()
    }
    SquadcallTheme {
        val navigator = rememberAppNavigator()
        // The consent form, once a space has ads, then the ads SDK (Android).
        AdsConsentEffect()
        // The mark on the splash and login stands on a white tile, as on the launch splash and the icon.
        CompositionLocalProvider(LocalAppNavigator provides navigator, LocalMarkOnTile provides true) {
            // Nothing opens until the server's feature flags have been read (TECHNICAL_DECISIONS §82).
            FeatureFlagsGate { CoachNavigation(navigator) }
        }
    }
}
