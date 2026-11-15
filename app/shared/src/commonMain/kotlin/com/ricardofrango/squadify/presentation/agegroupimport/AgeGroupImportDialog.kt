package com.ricardofrango.squadify.presentation.agegroupimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.domain.repository.AgeGroupImportRepository
import com.ricardofrango.squadify.model.ImportableAgeGroup
import com.ricardofrango.squadify.presentation.ui.components.DialogSecondaryButton
import com.ricardofrango.squadify.presentation.ui.components.SquadifyDialog
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_cancel
import squadify.app.shared.generated.resources.btn_import_age_group
import squadify.app.shared.generated.resources.import_dialog_empty
import squadify.app.shared.generated.resources.import_dialog_failed
import squadify.app.shared.generated.resources.import_dialog_hint
import squadify.app.shared.generated.resources.import_dialog_imported
import squadify.app.shared.generated.resources.import_dialog_import_failed
import squadify.app.shared.generated.resources.import_dialog_loading
import squadify.app.shared.generated.resources.import_dialog_title

/**
 * The age groups another app offers to copy into [clubId] (TECHNICAL_DECISIONS §81): each with an Import button, or
 * "Imported" when this club already has its copy. One copy per permission: the grant is used up by it, so after an
 * import the dialog closes ([onImported]); to copy another, the other app is asked again.
 */
@Composable
fun AgeGroupImportDialog(clubId: String, handoff: AgeGroupHandoff, onImported: () -> Unit, onDismiss: () -> Unit) {
    val repository: AgeGroupImportRepository = koinInject()
    val scope = rememberCoroutineScope()
    var offered by remember { mutableStateOf<List<ImportableAgeGroup>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<String?>(null) }
    var importFailed by remember { mutableStateOf(false) }

    LaunchedEffect(clubId, handoff) {
        try {
            offered = repository.listSourceAgeGroups(clubId, handoff.sourceUrl, handoff.grant)
        } catch (e: Exception) {
            loadFailed = true
        }
    }

    SquadifyDialog(
        title = stringResource(Res.string.import_dialog_title),
        body = stringResource(Res.string.import_dialog_hint),
        onDismiss = onDismiss,
        content = {
            val list = offered
            when {
                loadFailed -> Text(stringResource(Res.string.import_dialog_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                list == null -> Text(stringResource(Res.string.import_dialog_loading), style = MaterialTheme.typography.bodyMedium, color = CarbonGray)
                list.isEmpty() -> Text(stringResource(Res.string.import_dialog_empty), style = MaterialTheme.typography.bodyMedium, color = CarbonGray)
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.forEach { ageGroup ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ageGroup.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                                Text(
                                    "${ageGroup.clubName} · ${ageGroup.startYear}${if (ageGroup.endYear != ageGroup.startYear) "–${ageGroup.endYear}" else ""} · ${ageGroup.seasonName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CarbonGray
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (ageGroup.alreadyImported) {
                                Text(stringResource(Res.string.import_dialog_imported), style = MaterialTheme.typography.bodySmall, color = CarbonGray)
                            } else {
                                val busy = importing != null
                                TextButton(
                                    enabled = !busy,
                                    onClick = {
                                        importing = ageGroup.id
                                        importFailed = false
                                        scope.launch {
                                            try {
                                                repository.importAgeGroup(clubId, handoff.sourceUrl, ageGroup.id, handoff.grant)
                                                onImported()
                                            } catch (e: Exception) {
                                                importing = null
                                                importFailed = true
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(Res.string.btn_import_age_group), color = PitchGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (importFailed) {
                        Text(stringResource(Res.string.import_dialog_import_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        buttons = {
            DialogSecondaryButton(stringResource(Res.string.btn_cancel), Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    )
}
