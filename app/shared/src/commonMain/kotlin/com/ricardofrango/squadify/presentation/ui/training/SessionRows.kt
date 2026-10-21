package com.ricardofrango.squadify.presentation.ui.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.shortDateLabel
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.chip_cancelled
import squadify.app.shared.generated.resources.chip_needs_ratings
import squadify.app.shared.generated.resources.chip_next
import squadify.app.shared.generated.resources.chip_today
import squadify.app.shared.generated.resources.label_n_exercises
import squadify.app.shared.generated.resources.msg_no_exercises_yet

// The session rows of the Training tab (design 39a/b), using the match row's day block.

/** Upcoming soonest first (today included), past newest first. */
internal fun splitSessions(sessions: List<TrainingSession>, now: LocalDateTime): Pair<List<TrainingSession>, List<TrainingSession>> {
    val upcoming = sessions.filter { it.date >= now.date }.sortedWith(compareBy({ it.date }, { it.startTime }))
    val past = sessions.filter { it.date < now.date }.sortedWith(compareByDescending<TrainingSession> { it.date }.thenByDescending { it.startTime })
    return upcoming to past
}

/** Whether the session has ended (its end time on its date has passed). */
internal fun TrainingSession.ended(now: LocalDateTime): Boolean =
    date < now.date || (date == now.date && endTime <= now.time)

enum class SessionChip { TODAY, NEXT, CANCELLED, NEEDS_RATINGS }

/** The row's chip: CANCELLED, TODAY, NEXT (the next one, only when nothing is today), or Needs ratings. */
internal fun chipFor(session: TrainingSession, now: LocalDateTime, next: TrainingSession?, poolSize: Int?, canRate: Boolean): SessionChip? = when {
    session.cancelled -> SessionChip.CANCELLED
    session.date == now.date -> SessionChip.TODAY
    session.id == next?.id -> SessionChip.NEXT
    canRate && poolSize != null && session.ended(now) && (session.ratedCount ?: 0) < poolSize -> SessionChip.NEEDS_RATINGS
    else -> null
}

@Composable
fun SessionRow(session: TrainingSession, chip: SessionChip?, onClick: () -> Unit) {
    val highlighted = chip == SessionChip.TODAY
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) PitchGreen.copy(alpha = 0.06f) else Color.White,
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, if (highlighted) PitchGreen else CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(38.dp)) {
                Text(weekdayShort(session.date.dayOfWeek.isoDayNumber).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = PitchGreen)
                Text(session.date.day.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    timeRange(session.startTime, session.endTime),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (session.cancelled) TextDecoration.LineThrough else null
                    ),
                    color = if (session.cancelled) DeepCarbon.copy(alpha = 0.45f) else DeepCarbon
                )
                val meta = if (session.cancelled) {
                    listOfNotNull(shortDateLabel(session.date), session.location)
                } else {
                    listOfNotNull(
                        session.location,
                        if (session.exerciseCount > 0) pluralStringResource(Res.plurals.label_n_exercises, session.exerciseCount, session.exerciseCount)
                        else stringResource(Res.string.msg_no_exercises_yet)
                    )
                }
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            chip?.let { Spacer(Modifier.width(8.dp)); ChipLabel(it) }
            if (chip == null) session.averageRating?.let { avg ->
                Spacer(Modifier.width(8.dp))
                Text(
                    avg.oneDecimalString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                    color = Color.White,
                    modifier = Modifier.background(ratingColor(avg), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChipLabel(chip: SessionChip) {
    val (text, bg, fg) = when (chip) {
        SessionChip.TODAY -> Triple(stringResource(Res.string.chip_today), PitchGreen, Color.White)
        SessionChip.NEXT -> Triple(stringResource(Res.string.chip_next), PitchGreen.copy(alpha = 0.12f), PitchGreen)
        SessionChip.CANCELLED -> Triple(stringResource(Res.string.chip_cancelled), CarbonGray.copy(alpha = 0.1f), DeepCarbon.copy(alpha = 0.6f))
        SessionChip.NEEDS_RATINGS -> Triple(stringResource(Res.string.chip_needs_ratings), Amber.copy(alpha = 0.2f), AmberText)
    }
    Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = fg,
        modifier = Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}

/** "7.1". */
internal fun Double.oneDecimalString(): String {
    val tenths = kotlin.math.round(this * 10).toInt()
    return "${tenths / 10}.${tenths % 10}"
}

/** The match report bands (design 39m): 8+ solid green, 6–7.9 light green, under 6 amber, 0 grey. */
internal fun ratingColor(rating: Double): Color = when {
    rating <= 0.0 -> CarbonGray.copy(alpha = 0.35f)
    rating >= 8.0 -> SuccessGreen
    rating >= 6.0 -> Color(0xFF7CB342)
    else -> Amber
}
