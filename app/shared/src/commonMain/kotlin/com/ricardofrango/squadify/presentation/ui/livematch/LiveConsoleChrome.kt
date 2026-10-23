package com.ricardofrango.squadify.presentation.ui.livematch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.ricardofrango.squadify.presentation.ui.components.ClubLogo
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*

// The live console's frame, as drawn in the "Squadify Pitch View" design canvas: the scoreboard (a
// clock band over the names and scores upright, one bar lying down), the underline tabs, and the
// coloured buttons of the player sheet.

internal val PausedAmber = Color(0xFFFFB300)
private val YellowText = Color(0xFFFFC94D)
private val RedText = Color(0xFFFF8A80)

/** One side of the scoreboard; [onGoal] logs a goal for it (null: the score can't be tapped now). */
internal data class ScoreSide(val name: String, val logoUrl: String?, val score: Int, val onGoal: (() -> Unit)?)

/**
 * What the clock shows. [label] is the half ("1ST HALF · +2", "PAUSED"); [onClockClick] opens the
 * correction and [onPauseToggle] pauses or resumes (both null for a viewer who can't run the match).
 * At half time [halfTimeNote] replaces the clock ("1st half · 30+2'").
 */
internal data class ClockState(
    val clock: String,
    val label: String,
    val paused: Boolean,
    val halfTimeNote: String? = null,
    val onClockClick: (() -> Unit)? = null,
    val onPauseToggle: (() -> Unit)? = null
)

@Composable
private fun SideCrest(side: ScoreSide, size: Dp) {
    ClubLogo(
        side.logoUrl,
        side.name.take(3).uppercase(),
        size = size,
        background = if (side.logoUrl != null) Color.White else PitchGreen,
        initialsColor = Color.White,
        initialsStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 7.sp)
    )
}

@Composable
private fun ScoreBoxes(home: ScoreSide, away: ScoreSide, fontSize: TextUnit, minWidth: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(home, away).forEach { side ->
            Surface(
                onClick = side.onGoal ?: {},
                enabled = side.onGoal != null,
                shape = RoundedCornerShape(8.dp),
                color = PitchGreen
            ) {
                Text(
                    side.score.toString(),
                    modifier = Modifier.widthIn(min = minWidth).padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** The pause button: two bars while running, a play triangle (amber) while paused. */
@Composable
private fun PauseButton(paused: Boolean, size: Dp, onClick: () -> Unit) {
    val description = stringResource(if (paused) Res.string.lm_resume_cd else Res.string.lm_pause_cd)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (paused) PausedAmber.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
        modifier = Modifier.size(size).semantics { contentDescription = description }
    ) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            if (paused) {
                val path = Path().apply {
                    moveTo(s * 0.38f, s * 0.3f)
                    lineTo(s * 0.72f, s * 0.5f)
                    lineTo(s * 0.38f, s * 0.7f)
                    close()
                }
                drawPath(path, PausedAmber)
            } else {
                val w = s * 0.11f
                drawRect(Color.White, topLeft = Offset(s * 0.36f, s * 0.3f), size = androidx.compose.ui.geometry.Size(w, s * 0.4f))
                drawRect(Color.White, topLeft = Offset(s * 0.53f, s * 0.3f), size = androidx.compose.ui.geometry.Size(w, s * 0.4f))
            }
        }
    }
}

@Composable
private fun ClockText(clock: ClockState, fontSize: TextUnit) {
    Text(
        clock.clock,
        color = if (clock.paused) PausedAmber else Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .then(if (clock.onClockClick != null) Modifier.clickable(onClick = clock.onClockClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

/** Upright: the clock band (amber while paused) over the crests, names and the two score boxes. */
@Composable
internal fun ScoreboardCard(home: ScoreSide, away: ScoreSide, clock: ClockState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(40.dp)
                .background(if (clock.paused) PausedAmber.copy(alpha = 0.14f) else CarbonGray, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 12.dp)
        ) {
            if (clock.halfTimeNote != null) {
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.label_half_time_badge), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(clock.halfTimeNote, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            } else {
                Text(
                    clock.label,
                    color = if (clock.paused) PausedAmber else Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClockText(clock, 18.sp)
                    clock.onPauseToggle?.let { PauseButton(clock.paused, 30.dp, it) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .background(CarbonGray, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                SideCrest(home, 22.dp)
                Spacer(Modifier.width(6.dp))
                Text(home.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.padding(horizontal = 12.dp)) { ScoreBoxes(home, away, 28.sp, 36.dp) }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(away.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                SideCrest(away, 22.dp)
            }
        }
    }
}

/** Lying down: one bar -- home, the scores, away, then the clock in a pill. */
@Composable
internal fun ScoreboardBar(home: ScoreSide, away: ScoreSide, clock: ClockState, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).background(CarbonGray, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            SideCrest(home, 22.dp)
            Spacer(Modifier.width(6.dp))
            Text(home.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ScoreBoxes(home, away, 20.sp, 30.dp)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Text(away.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(6.dp))
            SideCrest(away, 22.dp)
        }
        Row(
            Modifier
                .background(if (clock.paused) PausedAmber.copy(alpha = 0.14f) else CarbonGray, RoundedCornerShape(10.dp))
                .border(1.dp, if (clock.paused) PausedAmber.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .padding(start = 8.dp, end = 3.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (clock.halfTimeNote != null) {
                Text(stringResource(Res.string.label_half_time_badge), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp, top = 4.dp, bottom = 4.dp))
            } else {
                ClockText(clock, 17.sp)
                clock.onPauseToggle?.let { PauseButton(clock.paused, 26.dp, it) } ?: Spacer(Modifier.width(4.dp))
            }
        }
    }
}

/** Underline tabs across the width: the selected one green with a 2dp line under it. */
@Composable
internal fun ConsoleTabs(tabs: List<Pair<String, String?>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val divider = Color.White.copy(alpha = 0.12f)
    Row(
        modifier.fillMaxWidth().drawBehind {
            drawLine(divider, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
    ) {
        tabs.forEachIndexed { index, (label, count) ->
            val isSelected = index == selected
            val color = if (isSelected) PitchGreen else Color.White.copy(alpha = 0.55f)
            Box(
                Modifier.weight(1f).clickable { onSelect(index) }.then(
                    if (isSelected) Modifier.drawBehind {
                        drawLine(color, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), strokeWidth = 2.dp.toPx())
                    } else Modifier
                ).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(label, color = color, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
                    count?.let {
                        Spacer(Modifier.width(4.dp))
                        Text(it, color = color, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** The player sheet's actions: one coloured tile each, two to a row (or a whole row). */
internal enum class ActionTone { GOAL, PLAIN, YELLOW, RED }

@Composable
internal fun ActionTile(label: String, tone: ActionTone, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (background, text) = when (tone) {
        ActionTone.GOAL -> PitchGreen to Color.White
        ActionTone.PLAIN -> Color.White.copy(alpha = 0.06f) to Color.White
        ActionTone.YELLOW -> YellowText.copy(alpha = 0.14f) to YellowText
        ActionTone.RED -> Color(0xFFC62828).copy(alpha = 0.14f) to RedText
    }
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp), color = background, modifier = modifier.heightIn(min = 46.dp)) {
        Box(Modifier.padding(12.dp), contentAlignment = Alignment.CenterStart) {
            Text(label, color = text.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun ActionTileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Lying down, the bench is a column of rows: number (or photo), name, and the minutes bar along the foot. */
@Composable
internal fun BenchRowToken(number: Int?, photo: @Composable () -> Unit, name: String, minutesFraction: Float?, highlighted: Boolean, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .then(if (highlighted) Modifier.border(BorderStroke(2.dp, PitchGreen), RoundedCornerShape(8.dp)) else Modifier)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (number != null) {
                Box(Modifier.size(26.dp).background(Color(0xFF2C2C2E), CircleShape), contentAlignment = Alignment.Center) {
                    Text(number.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                photo()
            }
            Spacer(Modifier.width(8.dp))
            Text(name, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        minutesFraction?.let { fraction ->
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(PitchGreen))
            }
        }
    }
}
