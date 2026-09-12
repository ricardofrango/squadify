package com.ricardofrango.clubmanager.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricardofrango.clubmanager.model.Match
import com.ricardofrango.clubmanager.model.MatchScore
import com.ricardofrango.clubmanager.model.MatchStatus
import com.ricardofrango.clubmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.clubmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.clubmanager.presentation.ui.theme.PitchGreen
import clubmanager.app.shared.generated.resources.Res
import clubmanager.app.shared.generated.resources.label_result_draw
import clubmanager.app.shared.generated.resources.label_result_loss
import clubmanager.app.shared.generated.resources.label_result_win
import clubmanager.app.shared.generated.resources.matches_full_time
import clubmanager.app.shared.generated.resources.matches_half_time
import clubmanager.app.shared.generated.resources.matches_live
import clubmanager.app.shared.generated.resources.matches_minute
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The one match-row look used everywhere a match can appear in a list: the club Matches tab, Age
 * Group Details' "Next games", and Team Details' upcoming/results lists. Rendering branches entirely
 * on `match.status` -- SCHEDULED shows a date/time block, LIVE/HALF_TIME shows a live badge with the
 * running minute, FINISHED shows a muted date plus either the final score (win/loss/draw colored) or
 * a plain "FULL TIME" chip if [score] wasn't available, and CANCELLED shows a muted chip. Every caller
 * feeds the same three pieces of screen-specific context: who the opponent is, whether to show a team
 * name (omit it -- pass null -- on a screen already scoped to one team), and the match's score, which
 * is never on `Match` itself (it's derived from match events) -- callers fetch/cache it separately
 * (see ClubDetailsViewModel.matchScores, AgeGroupDetails.upcomingMatchScores,
 * TeamDetails.upcomingMatchScores) and pass null when there isn't one yet (e.g. a SCHEDULED match, or
 * a best-effort fetch that failed).
 */
@Composable
fun MatchRow(
    match: Match,
    opponentName: String,
    teamName: String? = null,
    score: MatchScore? = null,
    onClick: () -> Unit
) {
    val live = match.status == MatchStatus.LIVE || match.status == MatchStatus.HALF_TIME
    val finished = match.status == MatchStatus.FINISHED
    val cancelled = match.status == MatchStatus.CANCELLED

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MatchRowShape,
        color = Color.White,
        border = BorderStroke(if (live) 2.dp else 1.dp, if (live) PitchGreen else CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            when {
                live -> LiveDateBlock(match)
                else -> ScheduledDateBlock(match.dateTime, muted = finished || cancelled)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (teamName != null && teamName.isNotEmpty()) {
                    Text(
                        teamName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "vs $opponentName",
                    style = if (teamName != null && teamName.isNotEmpty()) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    },
                    color = if (teamName != null && teamName.isNotEmpty()) DeepCarbon.copy(alpha = 0.8f) else DeepCarbon,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitleFor(match, live, finished),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            when {
                finished -> {
                    Spacer(Modifier.width(8.dp))
                    if (score != null) {
                        val accent = resultColor(score)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${score.ourScore}–${score.opponentScore}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = accent
                            )
                            Text(
                                stringResource(resultLabel(score)),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accent.copy(alpha = 0.8f)
                            )
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(6.dp), color = CarbonGray.copy(alpha = 0.12f)) {
                            Text(
                                stringResource(Res.string.matches_full_time),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = DeepCarbon.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                live && score != null -> {
                    // Not decided yet -- show the running score itself, no WIN/LOSS/DRAW label.
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${score.ourScore}–${score.opponentScore}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = resultColor(score)
                    )
                }
            }
        }
    }
}

private val MatchRowShape = RoundedCornerShape(16.dp)
private val LiveRed = Color(0xFFC0392B)

@Composable
private fun LiveDateBlock(match: Match) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
        Surface(shape = RoundedCornerShape(4.dp), color = LiveRed) {
            Text(
                stringResource(Res.string.matches_live),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            // The trailing apostrophe is appended here, not baked into the string resource -- the
            // Compose Multiplatform resource generator doesn't reliably unescape a trailing \' in a
            // formatted string, which rendered as a literal "3\'" instead of "3'".
            if (match.status == MatchStatus.HALF_TIME) stringResource(Res.string.matches_half_time)
            else "${stringResource(Res.string.matches_minute, liveMinute(match.kickoffAt ?: match.dateTime))}'",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = PitchGreen
        )
    }
}

@Composable
private fun ScheduledDateBlock(instant: Instant, muted: Boolean) {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val alpha = if (muted) 0.5f else 1f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
        Text(
            dt.dayOfWeek.name.take(3),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = PitchGreen.copy(alpha = alpha)
        )
        Text(
            dt.day.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon.copy(alpha = alpha)
        )
    }
}

private fun subtitleFor(match: Match, live: Boolean, finished: Boolean): String {
    val loc = match.location?.takeIf { it.isNotBlank() }
    return when {
        live -> loc ?: ""
        finished -> match.dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date.let(::dayMonthLabel)
        else -> listOfNotNull(timeLabel(match.dateTime), loc).joinToString(" · ")
    }
}

private fun resultColor(score: MatchScore): Color = when {
    score.ourScore > score.opponentScore -> PitchGreen
    score.ourScore < score.opponentScore -> LiveRed
    else -> CarbonGray.copy(alpha = 0.6f)
}

private fun resultLabel(score: MatchScore): StringResource = when {
    score.ourScore > score.opponentScore -> Res.string.label_result_win
    score.ourScore < score.opponentScore -> Res.string.label_result_loss
    else -> Res.string.label_result_draw
}

private fun liveMinute(kickoff: Instant): Int {
    val mins = (Clock.System.now() - kickoff).inWholeMinutes
    return mins.coerceIn(1, 130).toInt()
}

private fun timeLabel(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

private val MONTH_ABBR = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private fun dayMonthLabel(date: kotlinx.datetime.LocalDate): String = "${date.day} ${MONTH_ABBR[date.month.ordinal]}"
