package com.ricardofrango.squadify.presentation.ui.components

import squadify.app.shared.generated.resources.match_type_abbr_tournament
import squadify.app.shared.generated.resources.match_type_abbr_championship
import squadify.app.shared.generated.resources.match_type_abbr_friendly
import squadify.app.shared.generated.resources.match_row_half_time
import squadify.app.shared.generated.resources.status_cancelled
import androidx.compose.foundation.layout.Box
import kotlinx.datetime.isoDayNumber
import com.ricardofrango.squadify.presentation.ui.training.weekdayShort
import com.ricardofrango.squadify.model.MatchType
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.ourSideFirst
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.elapsedMatchSeconds
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.Res
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
 * Group and Team Details, Opponent details. Laid out as football lists a fixture (the developer's
 * design, 2026-10-23):
 * - on the left, the weekday, "24 Oct" and the type in three letters (Fri / Cha / Tou);
 * - in the middle, the home side over the away side, each with its logo when it has one -- our club
 *   first at home and on neutral ground ([MatchVenue.ourSideFirst]);
 * - on the right, the kickoff time before the game; the score, one per side, once it's played
 *   (black when finished; red while live, with the minute beside it; yellow at half time, with "HT").
 * [score] is never on `Match` itself (it's derived from match events) -- callers fetch/cache it
 * separately and pass null when there isn't one yet.
 */
@Composable
fun MatchRow(
    match: Match,
    opponentName: String,
    // Our side's name -- the team. Every caller passes it now, since both sides are named.
    teamName: String? = null,
    score: MatchScore? = null,
    // The opponent's and our club's logo paths, shown before each name when set.
    opponentLogoUrl: String? = null,
    clubLogoUrl: String? = null,
    onClick: () -> Unit
) {
    val live = match.status == MatchStatus.LIVE || match.status == MatchStatus.HALF_TIME
    val finished = match.status == MatchStatus.FINISHED
    val cancelled = match.status == MatchStatus.CANCELLED

    val us = RowSide(teamName.orEmpty(), clubLogoUrl, score?.ourScore)
    val them = RowSide(opponentName, opponentLogoUrl, score?.opponentScore)
    val (home, away) = if (match.venue.ourSideFirst()) us to them else them to us
    val scoreColor = when {
        match.status == MatchStatus.HALF_TIME -> HalfTimeYellow
        live -> LiveRed
        else -> DeepCarbon
    }
    val showScore = score != null && (live || finished)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MatchRowShape,
        color = Color.White,
        border = BorderStroke(if (live) 2.dp else 1.dp, if (live) PitchGreen else CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DateBlock(match, muted = finished || cancelled)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SideLine(home, faded = cancelled)
                SideLine(away, faded = cancelled)
            }
            Spacer(Modifier.width(8.dp))
            when {
                cancelled -> Text(
                    stringResource(Res.string.status_cancelled),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon.copy(alpha = 0.45f)
                )
                showScore -> {
                    if (live) {
                        LiveMinute(match, color = scoreColor)
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ScoreCell(home.score, scoreColor)
                        ScoreCell(away.score, scoreColor)
                    }
                }
                finished || live -> Text("—", style = MaterialTheme.typography.titleMedium, color = DeepCarbon.copy(alpha = 0.4f))
                else -> Text(
                    timeLabel(match.dateTime),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon
                )
            }
        }
    }
}

private data class RowSide(val name: String, val logoUrl: String?, val score: Int?)

// Each side's line and its score cell share a height, so the scores sit level with the names.
private val SideLineHeight = 24.dp

@Composable
private fun SideLine(side: RowSide, faded: Boolean) {
    Row(Modifier.height(SideLineHeight), verticalAlignment = Alignment.CenterVertically) {
        side.logoUrl?.let {
            ClubLogo(it, side.name.take(2).uppercase(), size = 20.dp, background = CarbonGray.copy(alpha = 0.08f), initialsColor = DeepCarbon)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            side.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = DeepCarbon.copy(alpha = if (faded) 0.5f else 1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScoreCell(score: Int?, color: Color) {
    Box(Modifier.height(SideLineHeight), contentAlignment = Alignment.CenterEnd) {
        Text(score?.toString() ?: "–", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = color)
    }
}

private val MatchRowShape = RoundedCornerShape(16.dp)
private val LiveRed = Color(0xFFC0392B)
private val HalfTimeYellow = Color(0xFFE0A800)

/** Weekday, "24 Oct" (the day's big type) and the type in three letters, under each other. */
@Composable
private fun DateBlock(match: Match, muted: Boolean) {
    val date = match.dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val alpha = if (muted) 0.5f else 1f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
        Text(
            weekdayShort(date.dayOfWeek.isoDayNumber),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = PitchGreen.copy(alpha = alpha)
        )
        Text(
            shortDateLabel(date),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon.copy(alpha = alpha),
            maxLines = 1
        )
        match.type?.let {
            Text(
                stringResource(it.abbreviationRes()),
                style = MaterialTheme.typography.labelSmall,
                color = DeepCarbon.copy(alpha = 0.55f * alpha)
            )
        }
    }
}

/**
 * The running minute beside a live score ("30'"), or "HT" at half time. Ticks every 15s while LIVE --
 * without that, it only updated when something else recomposed the row. 15s, not the console's 1s:
 * it only shows whole minutes, and a whole list ticking every second would be needlessly expensive.
 */
@Composable
private fun LiveMinute(match: Match, color: Color) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(match.id, match.status) {
        if (match.status == MatchStatus.LIVE) {
            while (true) {
                delay(15_000)
                tick++
            }
        }
    }
    // Shares elapsedMatchSeconds with the live console, so the two can never disagree.
    val minute = remember(tick, match) { (elapsedMatchSeconds(match, Clock.System.now()) / 60).coerceIn(0, 130) }
    Text(
        // The apostrophe is appended here, not baked into the string resource -- the resource
        // generator doesn't reliably unescape a trailing \' in a formatted string.
        if (match.status == MatchStatus.HALF_TIME) stringResource(Res.string.match_row_half_time) else "${stringResource(Res.string.matches_minute, minute)}'",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = color
    )
}

/** A match type in three letters, for the list's date column. */
private fun MatchType.abbreviationRes(): StringResource = when (this) {
    MatchType.FRIENDLY -> Res.string.match_type_abbr_friendly
    MatchType.CHAMPIONSHIP -> Res.string.match_type_abbr_championship
    MatchType.TOURNAMENT -> Res.string.match_type_abbr_tournament
}

private fun timeLabel(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

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
