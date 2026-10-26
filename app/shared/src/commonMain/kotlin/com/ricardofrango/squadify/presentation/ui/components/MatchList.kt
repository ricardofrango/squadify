package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import androidx.compose.foundation.background
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.label_recent_results
import squadify.app.shared.generated.resources.label_upcoming
import squadify.app.shared.generated.resources.msg_no_results_yet
import squadify.app.shared.generated.resources.msg_no_upcoming_matches
import squadify.app.shared.generated.resources.team_heading_count
import squadify.app.shared.generated.resources.team_schedule_short
import kotlin.time.Instant

// The match list of the Games / Matches tabs: a team's, an age group's and the club's. One look in all
// three: "Upcoming · n" then "Recent results · n".

/** One row: the match, its score once started, and its team's name (null leaves it off). */
class MatchListEntry(val match: Match, val score: MatchScore?, val teamName: String?)

/** One of the two sections. [total] also counts rows not loaded yet (the club's, a page at a time). */
class MatchListSection(val entries: List<MatchListEntry>, val total: Int = entries.size)

/**
 * "Upcoming · n" (with "Schedule" when [onSchedule] is given) and its rows, then "Recent results · n"
 * and its rows: every match the server sent, with no "See all" (the club's list loads more pages as it
 * scrolls). Each section shows a card when it's empty, and a null one is left out (the club's
 * Upcoming / Finished filter).
 */
fun LazyListScope.matchListSections(
    upcoming: MatchListSection?,
    results: MatchListSection?,
    opponentsById: Map<String, Opponent>,
    clubLogoUrl: String?,
    onOpenMatch: (String) -> Unit,
    onSchedule: (() -> Unit)? = null
) {
    if (upcoming != null) {
        item(key = "upcoming-heading") {
            MatchListHeading(stringResource(Res.string.team_heading_count, stringResource(Res.string.label_upcoming), upcoming.total), onSchedule)
        }
        if (upcoming.entries.isEmpty()) {
            item(key = "upcoming-empty") { EmptyMatchCard(stringResource(Res.string.msg_no_upcoming_matches)) }
        } else {
            items(upcoming.entries, key = { it.match.id }) { MatchListRow(it, opponentsById, clubLogoUrl, onOpenMatch) }
        }
    }
    if (results != null) {
        item(key = "results-heading") {
            MatchListHeading(
                stringResource(Res.string.team_heading_count, stringResource(Res.string.label_recent_results), results.total),
                onSchedule = null,
                modifier = Modifier.padding(top = if (upcoming != null) 6.dp else 0.dp)
            )
        }
        if (results.entries.isEmpty()) {
            item(key = "results-empty") { EmptyMatchCard(stringResource(Res.string.msg_no_results_yet)) }
        } else {
            items(results.entries, key = { it.match.id }) { MatchListRow(it, opponentsById, clubLogoUrl, onOpenMatch) }
        }
    }
}

@Composable
private fun MatchListHeading(title: String, onSchedule: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        StatsHeading(title, Modifier.weight(1f))
        if (onSchedule != null) {
            Text(
                stringResource(Res.string.team_schedule_short),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = PitchGreen,
                modifier = Modifier.clickable(onClick = onSchedule)
            )
        }
    }
}

@Composable
private fun EmptyMatchCard(text: String) {
    StatsCard(contentPadding = 16.dp) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = StatsSecondaryText)
    }
}

@Composable
private fun MatchListRow(entry: MatchListEntry, opponentsById: Map<String, Opponent>, clubLogoUrl: String?, onOpenMatch: (String) -> Unit) {
    val opponent = opponentsById[entry.match.opponentId]
    MatchRow(
        match = entry.match,
        opponentName = opponent?.let { it.shortName ?: it.name } ?: "—",
        opponentLogoUrl = opponent?.logoUrl,
        clubLogoUrl = clubLogoUrl,
        teamName = entry.teamName,
        score = entry.score,
        onClick = { onOpenMatch(entry.match.id) }
    )
}

// ---- Previews ----

private fun previewEntry(id: String, status: MatchStatus, score: MatchScore? = null) = MatchListEntry(
    Match(id, "c1", "t1", "o1", dateTime = Instant.fromEpochMilliseconds(1_793_000_000_000), status = status),
    score,
    "Sub-15 A"
)

@Composable
private fun MatchListPreviewFrame() {
    SquadifyTheme {
        LazyColumn(
            Modifier.fillMaxSize().background(ScreenBackground),
            contentPadding = PaddingValues(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = ScreenPadding.Top, bottom = ScreenPadding.Bottom),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            matchListSections(
                upcoming = MatchListSection(listOf(previewEntry("m1", MatchStatus.SCHEDULED))),
                results = MatchListSection(
                    listOf("m2", "m3", "m4", "m5").map { previewEntry(it, MatchStatus.FINISHED, MatchScore(it, 2, 1)) },
                    total = 12
                ),
                opponentsById = mapOf("o1" to Opponent("o1", "c1", "Académica")),
                clubLogoUrl = null,
                onOpenMatch = {},
                onSchedule = {}
            )
        }
    }
}

@Preview
@Composable
private fun MatchListPreview() = MatchListPreviewFrame()

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun MatchListLandscapePreview() = MatchListPreviewFrame()
