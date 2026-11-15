package com.ricardofrango.squadify.presentation.agegroupimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
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
import com.ricardofrango.squadify.model.ImportAgeGroupResult
import com.ricardofrango.squadify.model.ImportBlock
import com.ricardofrango.squadify.model.ImportableAgeGroup
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.components.AgeGroupsNotInPlanNotice
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
import squadify.app.shared.generated.resources.import_dialog_clash
import squadify.app.shared.generated.resources.import_dialog_loading
import squadify.app.shared.generated.resources.import_dialog_other_season
import squadify.app.shared.generated.resources.import_dialog_other_sport
import squadify.app.shared.generated.resources.import_dialog_title
import squadify.app.shared.generated.resources.sport_football
import squadify.app.shared.generated.resources.sport_futsal

/**
 * The age groups another app offers to copy into [clubId] (TECHNICAL_DECISIONS §81). Each has an Import button, or
 * "Imported" when this club already has its copy. An age group that can't be copied now stays in the list with its
 * button off and the reason under it: another sport ([clubSport] is this club's), or a birth-year clash. When the
 * space's plan has no room for another age group, [ownPlan] (the owner's, else null) says so at the top, with See plans.
 * One copy per permission: the grant is used up by it, so after an import the dialog closes ([onImported]).
 */
@Composable
fun AgeGroupImportDialog(
    clubId: String,
    clubSport: SportType,
    ownPlan: SpacePlan?,
    handoff: AgeGroupHandoff,
    onImported: (ImportAgeGroupResult) -> Unit,
    onDismiss: () -> Unit
) {
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
                    if (ownPlan != null && list.any { it.blocked == ImportBlock.NO_ROOM_IN_PLAN }) {
                        AgeGroupsNotInPlanNotice(ownPlan)
                    }
                    list.forEach { ageGroup ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ageGroup.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                                Text(
                                    "${ageGroup.clubName} · ${ageGroup.startYear}${if (ageGroup.endYear != ageGroup.startYear) "–${ageGroup.endYear}" else ""} · ${ageGroup.seasonName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CarbonGray
                                )
                                reasonFor(ageGroup, clubSport)?.let { reason ->
                                    Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            if (ageGroup.blocked == ImportBlock.ALREADY_IMPORTED) {
                                Text(stringResource(Res.string.import_dialog_imported), style = MaterialTheme.typography.bodySmall, color = CarbonGray)
                            } else {
                                val busy = importing != null
                                val canImport = !busy && ageGroup.blocked == null
                                TextButton(
                                    enabled = canImport,
                                    onClick = {
                                        importing = ageGroup.id
                                        importFailed = false
                                        scope.launch {
                                            try {
                                                onImported(repository.importAgeGroup(clubId, handoff.sourceUrl, ageGroup.id, handoff.grant))
                                            } catch (e: Exception) {
                                                importing = null
                                                importFailed = true
                                            }
                                        }
                                    }
                                ) {
                                    if (importing == ageGroup.id) {
                                        CircularProgressIndicator(Modifier.size(20.dp), color = PitchGreen, strokeWidth = 2.dp)
                                    } else {
                                        Text(
                                            stringResource(Res.string.btn_import_age_group),
                                            color = if (canImport) PitchGreen else CarbonGray.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
            // Not while a copy is running: it finishes, and then the dialog closes with the list refreshed.
            DialogSecondaryButton(stringResource(Res.string.btn_cancel), Modifier.fillMaxWidth(), onClick = { if (importing == null) onDismiss() })
        }
    )
}

/** Why [ageGroup] can't be copied, for its row under the name; null when it can, or when it's imported (shown on the button side). */
@Composable
private fun reasonFor(ageGroup: ImportableAgeGroup, clubSport: SportType): String? = when (ageGroup.blocked) {
    ImportBlock.OTHER_SPORT -> stringResource(Res.string.import_dialog_other_sport, sportName(ageGroup.sport), sportName(clubSport))
    ImportBlock.OTHER_SEASON -> stringResource(Res.string.import_dialog_other_season, ageGroup.seasonName)
    ImportBlock.BIRTH_YEARS_TAKEN -> {
        val start = ageGroup.clashStartYear ?: 0
        val end = ageGroup.clashEndYear ?: start
        val years = if (start == end) "$start" else "$start–$end"
        stringResource(Res.string.import_dialog_clash, ageGroup.clashName.orEmpty(), years)
    }
    else -> null
}

@Composable
private fun sportName(sport: SportType): String = stringResource(
    when (sport) {
        SportType.FOOTBALL -> Res.string.sport_football
        SportType.FUTSAL -> Res.string.sport_futsal
    }
)
