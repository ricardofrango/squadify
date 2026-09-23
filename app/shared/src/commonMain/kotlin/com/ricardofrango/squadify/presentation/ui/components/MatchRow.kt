package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.elapsedMatchSeconds
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.label_paused_badge
import squadify.app.shared.generated.resources.label_result_draw
import squadify.app.shared.generated.resources.label_result_loss
import squadify.app.shared.generated.resources.label_result_win
import squadify.app.shared.generated.resources.matches_full_time
import squadify.app.shared.generated.resources.matches_half_time
import squadify.app.shared.generated.resources.matches_live
import squadify.app.shared.generated.resources.matches_minute
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes

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
private val CardYellow = Color(0xFFF1C40F)

@Composable
private fun LiveDateBlock(match: Match) {
    // Ticks this row's own minute badge every 15s while the match is LIVE -- without this, the badge
    // only updated when something else forced a recomposition (a new club-socket event, or reopening
    // the tab), which read as "the timer doesn't update" (see TECHNICAL_DECISIONS.md's match-clock
    // section). 15s rather than the live console's 1s: this only ever displays whole minutes, and a
    // whole club Matches list of these ticking every second would be needlessly expensive.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(match.id, match.status) {
        if (match.status == MatchStatus.LIVE) {
            while (true) {
                delay(15_000)
                tick++
            }
        }
    }
    val isPaused = match.pausedAt != null
    // Shares elapsedMatchSeconds with the live match console (LiveMatchScreen) -- previously this used
    // a bare `now - kickoffAt` that ignored secondHalfStartedAt/halfLengthMinutes entirely, so it kept
    // counting straight through kickoff instead of showing the second-half-adjusted minute the console
    // showed for the same match.
    val minute = remember(tick, match) {
        (elapsedMatchSeconds(match, Clock.System.now()) / 60).coerceIn(0, 130)
    }

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
            when {
                match.status == MatchStatus.HALF_TIME -> stringResource(Res.string.matches_half_time)
                isPaused -> stringResource(Res.string.label_paused_badge)
                else -> "${stringResource(Res.string.matches_minute, minute)}'"
            },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isPaused) CardYellow else PitchGreen
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

private fun timeLabel(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

private val MONTH_ABBR = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private fun dayMonthLabel(date: kotlinx.datetime.LocalDate): String = "${date.day} ${MONTH_ABBR[date.month.ordinal]}"

private fun previewMatch(
    status: MatchStatus,
    kickoffAt: Instant? = null,
    secondHalfStartedAt: Instant? = null,
    pausedAt: Instant? = null,
    location: String? = "Municipal Stadium"
) = Match(
    id = "m1",
    clubId = "c1",
    teamId = "t1",
    opponentId = "o1",
    location = location,
    dateTime = Instant.fromEpochMilliseconds(0),
    status = status,
    halfLengthMinutes = if (secondHalfStartedAt != null) 45 else null,
    kickoffAt = kickoffAt,
    secondHalfStartedAt = secondHalfStartedAt,
    pausedAt = pausedAt
)

@Preview
@Composable
private fun MatchRowScheduledPreview() {
    SquadifyTheme {
        MatchRow(
            match = previewMatch(MatchStatus.SCHEDULED),
            opponentName = "Sporting Youth",
            teamName = "Sub 2015 A",
            onClick = {}
        )
    }
}

@Preview
@Composable
private fun MatchRowLivePreview() {
    SquadifyTheme {
        MatchRow(
            match = previewMatch(MatchStatus.LIVE, kickoffAt = Clock.System.now() - 23.minutes),
            opponentName = "Sporting Youth",
            teamName = "Sub 2015 A",
            score = MatchScore("m1", 2, 1),
            onClick = {}
        )
    }
}

@Preview
@Composable
private fun MatchRowHalfTimePreview() {
    SquadifyTheme {
        MatchRow(
            match = previewMatch(MatchStatus.HALF_TIME, kickoffAt = Clock.System.now() - 45.minutes),
            opponentName = "Sporting Youth",
            teamName = "Sub 2015 A",
            score = MatchScore("m1", 0, 0),
            onClick = {}
        )
    }
}

@Preview
@Composable
private fun MatchRowPausedPreview() {
    SquadifyTheme {
        MatchRow(
            match = previewMatch(
                MatchStatus.LIVE,
                kickoffAt = Clock.System.now() - 23.minutes,
                pausedAt = Clock.System.now() - 2.minutes
            ),
            opponentName = "Sporting Youth",
            teamName = "Sub 2015 A",
            score = MatchScore("m1", 1, 1),
            onClick = {}
        )
    }
}

@Preview
@Composable
private fun MatchRowFinishedResultsPreview() {
    SquadifyTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MatchRow(
                match = previewMatch(MatchStatus.FINISHED),
                opponentName = "Sporting Youth (win)",
                score = MatchScore("m1", 3, 1),
                onClick = {}
            )
            MatchRow(
                match = previewMatch(MatchStatus.FINISHED),
                opponentName = "Benfica Youth (loss)",
                score = MatchScore("m1", 0, 2),
                onClick = {}
            )
            MatchRow(
                match = previewMatch(MatchStatus.FINISHED),
                opponentName = "Porto Youth (draw)",
                score = MatchScore("m1", 1, 1),
                onClick = {}
            )
            // Score fetch failed / not available -- falls back to a plain "FULL TIME" chip.
            MatchRow(
                match = previewMatch(MatchStatus.FINISHED),
                opponentName = "Braga Youth (no score)",
                onClick = {}
            )
        }
    }
}

@Preview
@Composable
private fun MatchRowCancelledPreview() {
    SquadifyTheme {
        MatchRow(
            match = previewMatch(MatchStatus.CANCELLED, location = null),
            opponentName = "Sporting Youth",
            teamName = "Sub 2015 A",
            onClick = {}
        )
    }
}

/** Team Details' rows are already scoped to one team, so [teamName] is omitted. */
@Preview
@Composable
private fun MatchRowNoTeamNamePreview() {
    SquadifyTheme {
        MatchRow(
            match = previewMatch(MatchStatus.SCHEDULED),
            opponentName = "Sporting Youth",
            onClick = {}
        )
    }
}
