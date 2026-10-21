package com.ricardofrango.squadify

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.ricardofrango.squadify.presentation.navigation.AppNavigation
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
        AppNavigation()
    }
}
