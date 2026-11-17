package com.ricardofrango.squadify.presentation.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.ricardofrango.squadify.domain.config.AppConfig
import com.ricardofrango.squadify.domain.config.FeatureFlagsStatus
import com.ricardofrango.squadify.domain.config.FeatureFlagsStore
import com.ricardofrango.squadify.presentation.ui.components.FullScreenMessage
import com.ricardofrango.squadify.presentation.ui.components.OnReturnToApp
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.btn_update
import squadify.app.shared.generated.resources.ic_cloud_off
import squadify.app.shared.generated.resources.msg_check_connection
import squadify.app.shared.generated.resources.startup_needs_connection_title
import squadify.app.shared.generated.resources.startup_update_body
import squadify.app.shared.generated.resources.startup_update_title

/**
 * The root gate of both apps (TECHNICAL_DECISIONS §82): [content], the app's navigation, is shown only once the
 * server's feature flags have been read. Until then the carbon splash look (on Android, the system splash is still
 * up); when the read fails, a message asks for a connection, with Retry. The session is never touched here. The
 * read is tried again when a connection comes back and when the app is in front again.
 */
@Composable
fun FeatureFlagsGate(content: @Composable () -> Unit) {
    val store = koinInject<FeatureFlagsStore>()
    val status by store.status.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val storeUrl = koinInject<AppConfig>().storeUrl
    LaunchedEffect(Unit) { store.load() }
    OnReturnToApp { scope.launch { store.retryIfFailed() } }
    OnNetworkAvailable { scope.launch { store.retryIfFailed() } }
    when (status) {
        FeatureFlagsStatus.LOADED -> content()
        FeatureFlagsStatus.LOADING -> Box(Modifier.fillMaxSize().background(DeepCarbon))
        FeatureFlagsStatus.FAILED -> FullScreenMessage(
            icon = Res.drawable.ic_cloud_off,
            title = stringResource(Res.string.startup_needs_connection_title),
            body = stringResource(Res.string.msg_check_connection),
            buttonLabel = stringResource(Res.string.btn_retry),
            primary = true,
            onClick = { scope.launch { store.load() } },
        )
        // This build is too old (TECHNICAL_DECISIONS §85): the store's page, and nothing else until it's updated.
        FeatureFlagsStatus.UPDATE_REQUIRED -> FullScreenMessage(
            icon = Res.drawable.ic_cloud_off,
            title = stringResource(Res.string.startup_update_title),
            body = stringResource(Res.string.startup_update_body),
            buttonLabel = stringResource(Res.string.btn_update),
            primary = true,
            onClick = { storeUrl?.let(uriHandler::openUri) },
        )
    }
}

/**
 * Runs [onAvailable] each time a network connection becomes available (Android). Where there's no such signal
 * (iOS for now, web, desktop), it never runs: the app still retries on Retry and when it comes back to the front.
 */
@Composable
expect fun OnNetworkAvailable(onAvailable: () -> Unit)
