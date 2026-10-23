package com.ricardofrango.squadify.presentation.ui.matchreport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.MatchPlayerStatsWithPlayer
import com.ricardofrango.squadify.presentation.ui.components.RowPhoto
import com.ricardofrango.squadify.presentation.ui.components.initialsOf
import com.ricardofrango.squadify.presentation.ui.components.labelRes
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.DeepGreen
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*

internal val ReportCardShape = RoundedCornerShape(16.dp)
internal val ReportCardBorder = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f))

// 62%, not 55%: 55% DeepCarbon on white measures under the 4.5:1 floor for small text (design 23d).
internal val SecondaryText = DeepCarbon.copy(alpha = 0.62f)

/** White bordered card, the report's building block. */
@Composable
internal fun ReportCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = ReportCardShape, color = Color.White, border = ReportCardBorder) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** Small uppercase section heading with an optional trailing note ("2 of 5 filled"). */
@Composable
internal fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = SecondaryText, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = SecondaryText)
    }
}

/** A rating in its colour band (design 25d); unrated is an outlined grey "—". */
@Composable
internal fun RatingBadge(rating: Int?) {
    val (bg, fg) = when (rating?.let(::ratingBand)) {
        RatingBand.LOW -> AlertRed to Color.White
        RatingBand.MID -> Amber to CarbonGray
        RatingBand.GOOD -> SuccessGreen to Color.White
        RatingBand.TOP -> DeepGreen to Color.White
        null -> Color.White to SecondaryText
    }
    Surface(
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = if (rating == null) BorderStroke(1.5.dp, CarbonGray.copy(alpha = 0.2f)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                rating?.toString() ?: "—",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = fg
            )
        }
    }
}

/** Small tinted status pill (Started / Sub, G 1, ...). */
@Composable
internal fun MiniPill(text: String, bg: Color, fg: Color, swatch: Color? = null) {
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (swatch != null) {
                Box(Modifier.size(width = 7.dp, height = 10.dp).background(swatch, RoundedCornerShape(1.5.dp)))
                if (text.isNotEmpty()) Spacer(Modifier.width(3.dp))
            }
            if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp), color = fg)
        }
    }
}

/**
 * Shirt number, name, and the read-only stats line: position, Started/Sub, minutes, and chips for the
 * non-zero counts only (design 24e) -- goals, assists, own goals, a yellow swatch ("×2" for two), red.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerStatsHeader(row: MatchPlayerStatsWithPlayer, modifier: Modifier = Modifier, inline: Boolean = false) {
    val s = row.stats
    val p = row.player
    if (inline) {
        // Landscape editor (24c): number, name and stats on one line, the 0-10 row goes beneath.
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.shirtNumber?.toString() ?: "—", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = PitchGreen, modifier = Modifier.align(Alignment.CenterVertically))
            Text("${p.firstName} ${p.lastName}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, modifier = Modifier.align(Alignment.CenterVertically))
            StatsChips(row)
        }
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            p.shirtNumber?.toString() ?: "—",
            modifier = Modifier.width(30.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            color = PitchGreen
        )
        RowPhoto(p.photoUrl, initialsOf(p.firstName, p.lastName))
        Column(Modifier.weight(1f)) {
            Text(
                "${p.firstName} ${p.lastName}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatsChips(row)
            }
        }
    }
}

/** Position, Started/Sub, minutes, and chips for the non-zero counts only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.StatsChips(row: MatchPlayerStatsWithPlayer) {
    val s = row.stats
    val p = row.player
                Text(
                    p.position?.let { stringResource(it.labelRes()) } ?: stringResource(Res.string.report_no_position),
                    style = MaterialTheme.typography.bodySmall, color = SecondaryText,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                if (s.started) {
                    MiniPill(stringResource(Res.string.report_started), CarbonGray.copy(alpha = 0.08f), DeepCarbon)
                } else {
                    MiniPill(stringResource(Res.string.report_sub), Amber.copy(alpha = 0.2f), AmberText)
                }
                Text("${s.minutesPlayed}'", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SecondaryText, modifier = Modifier.align(Alignment.CenterVertically))
                if (s.goals > 0) MiniPill(stringResource(Res.string.report_chip_goals, s.goals), SuccessGreen.copy(alpha = 0.12f), DeepGreen)
                if (s.assists > 0) MiniPill(stringResource(Res.string.report_chip_assists, s.assists), CarbonGray.copy(alpha = 0.07f), DeepCarbon)
                if (s.ownGoals > 0) MiniPill(stringResource(Res.string.report_chip_own_goals, s.ownGoals), AlertRed.copy(alpha = 0.1f), AlertRed)
                if (s.yellowCards > 0) MiniPill(if (s.yellowCards > 1) "×${s.yellowCards}" else "", CarbonGray.copy(alpha = 0.05f), DeepCarbon, swatch = Amber)
                if (s.redCards > 0) MiniPill("", CarbonGray.copy(alpha = 0.05f), DeepCarbon, swatch = AlertRed)
}

/** The status chip in the dark header (New / Draft / Completed / Not saved / Pending). */
@Composable
internal fun HeaderStatusChip(text: String, tone: ChipTone, showDot: Boolean = true) {
    val (bg, fg) = when (tone) {
        ChipTone.AMBER -> Amber.copy(alpha = 0.22f) to Color(0xFFFFC94D)
        ChipTone.GREEN -> Color(0xFF4CAF50).copy(alpha = 0.2f) to Color(0xFF4CAF50)
        ChipTone.RED -> AlertRed.copy(alpha = 0.25f) to Color(0xFFFF8A80)
    }
    Surface(shape = RoundedCornerShape(9.dp), color = bg) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showDot) {
                Box(Modifier.size(6.dp).background(fg, CircleShape))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = fg)
        }
    }
}

internal enum class ChipTone { AMBER, GREEN, RED }
