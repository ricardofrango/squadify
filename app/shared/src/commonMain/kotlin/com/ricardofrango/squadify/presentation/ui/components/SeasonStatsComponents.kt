package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.presentation.ui.matchreport.RatingBand
import com.ricardofrango.squadify.presentation.ui.matchreport.ratingBand
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.DeepGreen
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.ic_expand_more
import squadify.app.shared.generated.resources.stats_filter_all
import squadify.app.shared.generated.resources.stats_filter_official
import squadify.app.shared.generated.resources.stats_filter_tournament
import squadify.app.shared.generated.resources.stats_empty_friendly
import squadify.app.shared.generated.resources.stats_empty_official
import squadify.app.shared.generated.resources.stats_empty_tournament
import squadify.app.shared.generated.resources.squad_empty_friendly
import squadify.app.shared.generated.resources.squad_empty_official
import squadify.app.shared.generated.resources.squad_empty_tournament
import squadify.app.shared.generated.resources.stats_filter_friendly
import kotlin.math.roundToInt

// Building blocks shared by Player Details' season stats (design 30) and Team Details' season record
// (design 31): the white card, its uppercase heading with the season pill, the All / Official /
// Tournament / Friendly segments, and the rating pill.

val StatsSecondaryText = DeepCarbon.copy(alpha = 0.62f)
private val StatsCardShape = RoundedCornerShape(16.dp)

@Composable
fun StatsCard(modifier: Modifier = Modifier, contentPadding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StatsCardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** "SEASON STATS" / "MATCHES" style heading. */
@Composable
fun StatsHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp),
        color = StatsSecondaryText
    )
}

/** The card's heading row, with the season pill on the right. [onPillClick] null renders it inert, without a chevron. */
@Composable
fun StatsHeaderRow(title: String, pillText: String?, onPillClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        StatsHeading(title, Modifier.weight(1f))
        if (pillText != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, CarbonGray.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .then(if (onPillClick != null) Modifier.clickable(onClick = onPillClick) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(pillText, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1)
                if (onPillClick != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(painterResource(Res.drawable.ic_expand_more), contentDescription = null, tint = DeepCarbon, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * All · Official · Tournament · Friendly, as one segmented control. Four labels don't fit side by side,
 * so the others show their first 3 letters ("Off", "Tou") and only the selected one its full name.
 * Switching animates: the segments' widths, the label and the dark background move together.
 */
@Composable
fun StatsFilterSegments(selected: StatsFilter, onSelected: (StatsFilter) -> Unit, modifier: Modifier = Modifier, bottomSpacing: Dp = 14.dp) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(bottom = bottomSpacing)
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, CarbonGray.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
    ) {
        StatsFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            val label = stringResource(filter.labelRes())
            val short = shortFilterLabel(label)
            // The selected segment takes the room its full name needs; the others, their 3 letters.
            val weight by animateFloatAsState(
                (if (isSelected) label.length else short.length).coerceAtLeast(3).toFloat() + 2f,
                animationSpec = tween(FilterAnimationMillis), label = "filterWeight"
            )
            val background by animateColorAsState(if (isSelected) CarbonGray else Color.White, tween(FilterAnimationMillis), label = "filterBackground")
            val textColor by animateColorAsState(if (isSelected) Color.White else DeepCarbon, tween(FilterAnimationMillis), label = "filterText")
            Box(
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .heightIn(min = 40.dp)
                    .background(background)
                    // The full name for screen readers, whatever is shown.
                    .semantics { role = Role.Tab; this.selected = isSelected; contentDescription = label }
                    .clickable { onSelected(filter) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = if (isSelected) label else short,
                    transitionSpec = { fadeIn(tween(FilterAnimationMillis)) togetherWith fadeOut(tween(FilterAnimationMillis / 2)) },
                    label = "filterLabel"
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

private const val FilterAnimationMillis = 250

/** "Official" → "Off", "Todos" → "Tod"; a label of 3 letters or fewer stays as it is ("All"). */
internal fun shortFilterLabel(label: String): String = label.take(3)

fun StatsFilter.labelRes() = when (this) {
    StatsFilter.ALL -> Res.string.stats_filter_all
    StatsFilter.OFFICIAL -> Res.string.stats_filter_official
    StatsFilter.TOURNAMENT -> Res.string.stats_filter_tournament
    StatsFilter.FRIENDLY -> Res.string.stats_filter_friendly
}

/** "No official matches this season", for a filter with nothing in it ([StatsFilter.ALL] reads as official). */
fun StatsFilter.emptyStatsRes() = when (this) {
    StatsFilter.FRIENDLY -> Res.string.stats_empty_friendly
    StatsFilter.TOURNAMENT -> Res.string.stats_empty_tournament
    StatsFilter.ALL, StatsFilter.OFFICIAL -> Res.string.stats_empty_official
}

/** "Nobody has played an official match this season", the squad list's empty state. */
fun StatsFilter.emptySquadRes() = when (this) {
    StatsFilter.FRIENDLY -> Res.string.squad_empty_friendly
    StatsFilter.TOURNAMENT -> Res.string.squad_empty_tournament
    StatsFilter.ALL, StatsFilter.OFFICIAL -> Res.string.squad_empty_official
}

/** A rating in the report's colour bands (design 25d); an average is banded by its rounded value. */
@Composable
fun RatingPill(value: Double, text: String, minWidth: Dp, height: Dp, fontSize: TextUnit, corner: Dp) {
    val (bg, fg) = when (ratingBand(value.roundToInt())) {
        RatingBand.LOW -> AlertRed to Color.White
        RatingBand.MID -> Amber to CarbonGray
        RatingBand.GOOD -> PitchGreen to Color.White
        RatingBand.TOP -> DeepGreen to Color.White
    }
    Box(
        Modifier.widthIn(min = minWidth).height(height).background(bg, RoundedCornerShape(corner)).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = fontSize), color = fg)
    }
}

/** One decimal, as the averages are shown ("7.8"). */
fun Double.oneDecimal(): String {
    val tenths = (this * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

/** Title + explanation inside a card -- the empty and error states of both cards. */
@Composable
fun StatsMessage(title: String, body: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = StatsSecondaryText)
        if (onRetry != null) {
            Spacer(Modifier.height(14.dp))
            Surface(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, CarbonGray.copy(alpha = 0.2f))
            ) {
                Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.btn_retry), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = CarbonGray)
                }
            }
        }
    }
}
