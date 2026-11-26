package com.sportssoftw.squadcall.presentation.agegroupimport

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sportssoftw.squadcall.domain.config.AppConfig
import com.sportssoftw.squadcall.domain.repository.AgeGroupImportRepository
import com.sportssoftw.squadcall.presentation.ui.components.ConfirmDialog
import com.sportssoftw.squadcall.presentation.ui.theme.ScreenBackground
import com.sportssoftw.squadcall.presentation.ui.theme.SquadcallTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import squadcall.app.shared.generated.resources.Res
import squadcall.app.shared.generated.resources.btn_cancel
import squadcall.app.shared.generated.resources.btn_export_allow
import squadcall.app.shared.generated.resources.export_consent_body
import squadcall.app.shared.generated.resources.export_consent_failed
import squadcall.app.shared.generated.resources.export_consent_title

/**
 * The exporting side of a copy (TECHNICAL_DECISIONS §81): the other app asks for a grant to read one of this user's age
 * groups. The user allows it here, and the grant and this server's address go back to the asking app. Nothing is
 * copied here.
 */
class ExportAgeGroupsActivity : ComponentActivity(), KoinComponent {

    private val repository: AgeGroupImportRepository by inject()
    private val appConfig: AppConfig by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only the Squadcall apps may ask for a grant: any other app that starts this screen is refused at once.
        val caller = callingActivity?.packageName
        if (caller == null || caller !in CLUBS_APP_PACKAGES + COACH_APP_PACKAGES) {
            finishWith(Activity.RESULT_CANCELED, null, null)
            return
        }
        setContent {
            SquadcallTheme {
                Box(Modifier.fillMaxSize().background(ScreenBackground))
                ExportConsent()
            }
        }
    }

    private fun finishWith(resultCode: Int, grant: String?, source: String?) {
        setResult(resultCode, Intent().apply {
            if (grant != null) putExtra(EXTRA_EXPORT_GRANT, grant)
            if (source != null) putExtra(EXTRA_EXPORT_SOURCE_URL, source)
        })
        finish()
    }

    @Composable
    private fun ExportConsent() {
        var failed by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        ConfirmDialog(
            title = stringResource(Res.string.export_consent_title),
            body = stringResource(if (failed) Res.string.export_consent_failed else Res.string.export_consent_body),
            onConfirm = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            // The grant is made by this app's own server, for the signed-in user.
                            val grant = repository.createExportGrant()
                            // This server's origin: the base address without the API version.
                            finishWith(Activity.RESULT_OK, grant.token, appConfig.baseUrl.removeSuffix("/v1"))
                        } catch (e: Exception) {
                            busy = false
                            failed = true
                        }
                    }
                }
            },
            onDismiss = { finishWith(Activity.RESULT_CANCELED, null, null) },
            confirmLabel = stringResource(Res.string.btn_export_allow),
            dismissLabel = stringResource(Res.string.btn_cancel),
            destructive = false
        )
    }
}
