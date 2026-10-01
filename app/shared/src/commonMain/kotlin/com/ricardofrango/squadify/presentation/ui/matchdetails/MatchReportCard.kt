package com.ricardofrango.squadify.presentation.ui.matchdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.MatchReportStatus
import com.ricardofrango.squadify.model.MatchReportSummary
import com.ricardofrango.squadify.presentation.ui.components.shortDateLabel
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.DeepGreen
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*

/**
 * The Match report card on a finished match (design 23). Three states:
 * pending and editable ("Fill in report"), completed ("View report", which carries its own Edit),
 * and read-only ("View report" -- disabled while pending, since there's nothing to read yet).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchReportCard(report: MatchReportSummary, onOpenReport: (edit: Boolean) -> Unit) {
    val completed = report.status == MatchReportStatus.COMPLETED
    val line = when {
        completed -> updatedLine(report)
        !report.canEdit -> stringResource(Res.string.report_card_readonly_pending)
        report.status == MatchReportStatus.DRAFT && report.ratablePlayers > 0 ->
            stringResource(Res.string.report_card_progress, report.ratedPlayers, report.ratablePlayers)
        else -> stringResource(Res.string.report_card_pending_hint)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(16.dp)) {
            // Icon + title on the left, badge on the right. Wraps rather than truncates: a long title
            // or a large system font drops the badge onto its own line (23b).
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(Res.drawable.ic_document), contentDescription = null, tint = DeepCarbon, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(Res.string.report_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = DeepCarbon
                    )
                }
                StatusBadge(completed)
            }
            Spacer(Modifier.height(6.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.62f))
            Spacer(Modifier.height(12.dp))
            if (report.canEdit && !completed) {
                Button(
                    onClick = { onOpenReport(true) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(painterResource(Res.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.report_card_fill), fontWeight = FontWeight.Black)
                }
            } else {
                OutlinedButton(
                    onClick = { onOpenReport(false) },
                    enabled = completed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, CarbonGray.copy(alpha = if (completed) 0.2f else 0.09f))
                ) {
                    Text(
                        stringResource(Res.string.report_card_view),
                        color = CarbonGray.copy(alpha = if (completed) 1f else 0.45f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(completed: Boolean) {
    val (bg, fg, dot) = if (completed) Triple(PitchGreen.copy(alpha = 0.12f), DeepGreen, PitchGreen)
    else Triple(Amber.copy(alpha = 0.2f), AmberText, Amber)
    Surface(shape = RoundedCornerShape(10.dp), color = bg) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(dot, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(
                stringResource(if (completed) Res.string.report_status_completed else Res.string.report_status_pending),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = fg
            )
        }
    }
}

@Composable
private fun updatedLine(report: MatchReportSummary): String {
    val at = report.updatedAt ?: return stringResource(Res.string.report_status_line_none)
    val tz = TimeZone.currentSystemDefault()
    val date = at.toLocalDateTime(tz).date
    val name = report.updatedByName.orEmpty()
    return if (date == Clock.System.now().toLocalDateTime(tz).date) {
        stringResource(Res.string.report_status_line_completed_today, name)
    } else {
        stringResource(Res.string.report_status_line_completed, shortDateLabel(date), name)
    }
}

@Preview
@Composable
private fun MatchReportCardStatesPreview() = SquadifyTheme {
    val at = Instant.parse("2026-09-28T09:00:00Z")
    Column(Modifier.background(Color(0xFFF5F5F5)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MatchReportCard(MatchReportSummary(MatchReportStatus.NOT_STARTED, ratedPlayers = 0, ratablePlayers = 12, canEdit = true)) {}
        MatchReportCard(MatchReportSummary(MatchReportStatus.DRAFT, at, "Nuno Ferreira", ratedPlayers = 3, ratablePlayers = 12, canEdit = true)) {}
        MatchReportCard(MatchReportSummary(MatchReportStatus.COMPLETED, at, "Nuno Ferreira", ratedPlayers = 12, ratablePlayers = 12, canEdit = true)) {}
        MatchReportCard(MatchReportSummary(MatchReportStatus.DRAFT, at, "Nuno Ferreira", ratedPlayers = 3, ratablePlayers = 12, canEdit = false)) {}
    }
}
