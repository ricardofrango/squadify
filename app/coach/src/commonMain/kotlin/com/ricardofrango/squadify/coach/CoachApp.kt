package com.ricardofrango.squadify.coach

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.ricardofrango.squadify.coach.presentation.navigation.CoachNavigation
import com.ricardofrango.squadify.presentation.navigation.LocalAppNavigator
import com.ricardofrango.squadify.presentation.navigation.rememberAppNavigator
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import io.ktor.client.HttpClient
import org.koin.compose.koinInject

/** Squadify for Coaches: every platform's coach entry point shows this. */
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
    SquadifyTheme {
        val navigator = rememberAppNavigator()
        CompositionLocalProvider(LocalAppNavigator provides navigator) {
            CoachNavigation(navigator)
        }
    }
}
