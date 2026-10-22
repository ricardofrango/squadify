package com.ricardofrango.squadify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.ricardofrango.squadify.presentation.navigation.AppNavigation
import com.ricardofrango.squadify.presentation.navigation.LocalAppNavController
import com.ricardofrango.squadify.presentation.navigation.LocalClubThemes
import com.ricardofrango.squadify.presentation.ui.theme.ClubThemeStore
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
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
    SquadifyTheme {
        // Each screen is drawn in the colours of the club it was opened under (ClubThemedEntry).
        val navController = rememberNavController()
        val clubThemes by koinInject<ClubThemeStore>().themes.collectAsState()
        CompositionLocalProvider(LocalAppNavController provides navController, LocalClubThemes provides clubThemes) {
            AppNavigation(navController)
        }
    }
}
