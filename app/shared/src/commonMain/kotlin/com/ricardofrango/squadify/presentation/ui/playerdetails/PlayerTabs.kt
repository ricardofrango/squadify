package com.ricardofrango.squadify.presentation.ui.playerdetails

import com.ricardofrango.squadify.presentation.ui.components.weekdayDateLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.ricardofrango.squadify.presentation.ui.components.RatingInfoButton
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.PlayerSeasonMatch
import com.ricardofrango.squadify.model.PlayerTrainingSeason
import com.ricardofrango.squadify.model.totals
import com.ricardofrango.squadify.model.PlayerTrainingSessionLine
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.presentation.ui.components.RatingPill
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsHeaderRow
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.twoDecimals
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import com.ricardofrango.squadify.presentation.ui.components.StatsMessage
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.training.PlayerTrainingCard
import com.ricardofrango.squadify.presentation.ui.training.ratingText
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*

/** The player screen's tabs: the player's info, the games with their ratings, and the training sessions. */
internal enum class PlayerTab(val label: StringResource) {
    GENERAL(Res.string.player_tab_general),
    GAMES(Res.string.player_tab_games),
    TRAINING(Res.string.player_tab_training)
}

/** Training only for those who see it (staff, clubs app): the server sends no seasons to anyone else. */
internal fun playerTabs(training: List<PlayerTrainingSeason>): List<PlayerTab> =
    if (training.isEmpty()) listOf(PlayerTab.GENERAL, PlayerTab.GAMES) else PlayerTab.entries

/** The training season to show: the one picked in the Games tab, else the latest with training. */
internal fun List<PlayerTrainingSeason>.forSeason(seasonId: String?): PlayerTrainingSeason? =
    firstOrNull { it.seasonId == seasonId } ?: firstOrNull()

/**
 * The General tab's overall ratings: every game and training rating of the season once, unrated ones left
 * out. A missed training (Absent) counts as 0 when [missedAsZero] -- the Full stats final ranking for one
 * player -- and is left out otherwise.
 */
internal fun overallRating(matches: List<PlayerSeasonMatch>, training: PlayerTrainingSeason?, missedAsZero: Boolean = true): Double? {
    val trainingRatings = training?.sessionLines.orEmpty().mapNotNull { it.rating }.filter { missedAsZero || it > 0.0 }
    val ratings = matches.mapNotNull { it.rating?.toDouble() } + trainingRatings
    return ratings.takeIf { it.isNotEmpty() }?.average()?.let { (it * 100).roundToInt() / 100.0 }
}

/**
 * General tab's summary: the overall ratings (staff), games played and goals, and, with training, the
 * sessions the player attended out of those held ("4/6"). [training] is the same season's, or null.
 */
@Composable
internal fun PlayerSummaryCard(state: SeasonStatsUiState.Loaded, training: PlayerTrainingSeason?, onOpenSeasons: () -> Unit) {
    val season = state.selectedSeason ?: return
    val totals = season.matches.totals(StatsFilter.ALL)
    StatsCard {
        StatsHeaderRow(stringResource(Res.string.player_summary_title), season.pillLabel(), onOpenSeasons, stacked = true)
        if (state.stats.canSeeRatings) {
            if (training == null) {
                // No training (the coach app): one average, the games'.
                RatingTile(overallRating(season.matches, null), stringResource(Res.string.player_overall_rating), Modifier.fillMaxWidth())
            } else {
                // Side by side: missed trainings left out, then counted as 0 (as in Full stats); the "i" explains.
                var explain by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RatingTile(overallRating(season.matches, training, missedAsZero = false), stringResource(Res.string.player_rating_attended_only), Modifier.weight(1f))
                    // One "i" between the two: the sheet explains both.
                    RatingInfoButton(stringResource(Res.string.player_averages_info)) { explain = true }
                    RatingTile(overallRating(season.matches, training), stringResource(Res.string.player_rating_missed_as_zero), Modifier.weight(1f))
                }
                if (explain) AveragesExplainSheet { explain = false }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = CarbonGray.copy(alpha = 0.08f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Headline(totals.apps.toString(), stringResource(Res.string.player_summary_games), Modifier.weight(1f))
            Headline(totals.goals.toString(), stringResource(Res.string.stats_goals), Modifier.weight(1f), color = SuccessGreen)
            if (training != null) {
                // Attended of the ended sessions: "6/6" when he was at every one.
                Headline("${training.attended}/${training.sessions}", stringResource(Res.string.player_summary_training), Modifier.weight(1f))
            }
        }
    }
}

/** A rating pill over its label, centred; "—" with nothing rated. */
@Composable
private fun RatingTile(rating: Double?, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (rating != null) {
            RatingPill(rating, rating.twoDecimals(), minWidth = 64.dp, height = 40.dp, fontSize = 22.sp, corner = 12.dp)
        } else {
            Text("—", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = StatsSecondaryText)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = StatsSecondaryText, textAlign = TextAlign.Center)
    }
}

/** How the General tab's two averages are worked out, with an example (the same sheet as Full stats' "i"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AveragesExplainSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            Text(stringResource(Res.string.player_averages_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            val body = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)
            Text(stringResource(Res.string.player_averages_intro), style = body, color = DeepCarbon.copy(alpha = 0.8f), modifier = Modifier.padding(top = 8.dp))
            DashLine(stringResource(Res.string.player_averages_left), body, DeepCarbon.copy(alpha = 0.8f))
            DashLine(stringResource(Res.string.player_averages_right), body, DeepCarbon.copy(alpha = 0.8f))
            Column(Modifier.padding(top = 12.dp).fillMaxWidth().background(CarbonGray.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                val example = body.copy(fontWeight = FontWeight.Medium)
                Text(stringResource(Res.string.player_averages_example), style = example, color = DeepCarbon)
                DashLine(stringResource(Res.string.player_averages_example_left), example, DeepCarbon)
                DashLine(stringResource(Res.string.player_averages_example_right), example, DeepCarbon)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ClubChrome, contentColor = Color.White)
            ) { Text(stringResource(Res.string.btn_ok)) }
        }
    }
}

/** An indented "–" line of the explanation. */
@Composable
private fun DashLine(text: String, style: androidx.compose.ui.text.TextStyle, color: Color) {
    Row(Modifier.padding(start = 12.dp, top = 6.dp)) {
        Text("–", style = style, color = color)
        Spacer(Modifier.width(8.dp))
        Text(text, style = style, color = color)
    }
}

/** Underline tabs on the dark header: the selected one in the chrome accent with a 2dp line under it. */
@Composable
internal fun PlayerTabRow(tabs: List<PlayerTab>, selected: PlayerTab, onSelect: (PlayerTab) -> Unit) {
    val divider = Color.White.copy(alpha = 0.12f)
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(divider, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            val color = if (isSelected) ChromeAccent else Color.White.copy(alpha = 0.6f)
            Box(
                Modifier.weight(1f).clickable { onSelect(tab) }.then(
                    if (isSelected) Modifier.drawBehind {
                        drawLine(color, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), strokeWidth = 2.dp.toPx())
                    } else Modifier
                ).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(tab.label), color = color, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/**
 * Games tab: the Season stats card as its header (season pill, filter and totals, or why there's nothing
 * yet), then the season's games in that filter, each with its rating for staff.
 */
@Composable
internal fun PlayerGamesTab(
    state: SeasonStatsUiState,
    playerFirstName: String,
    onRetry: () -> Unit,
    onOpenSeasons: () -> Unit,
    onFilterSelected: (StatsFilter) -> Unit,
    onOpenMatch: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerSeasonStatsCard(state, playerFirstName, onRetry, onOpenSeasons, onFilterSelected)
        val loaded = state as? SeasonStatsUiState.Loaded
        val matches = loaded?.visibleMatches().orEmpty()
        if (loaded != null && matches.isNotEmpty()) PlayerMatchesList(matches, loaded.stats.canSeeRatings, onOpenMatch)
    }
}


/** Training tab: the season's attendance and average as the header, then every ended session, missed ones included. */
@Composable
internal fun PlayerTrainingTab(season: PlayerTrainingSeason) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerTrainingCard(season)
        if (season.sessionLines.isEmpty()) {
            StatsCard(contentPadding = 16.dp) {
                StatsMessage(stringResource(Res.string.player_no_training_title), stringResource(Res.string.player_no_training_body))
            }
        } else {
            Column {
                StatsHeading(stringResource(Res.string.player_training_sessions), Modifier.padding(start = 2.dp, top = 4.dp, bottom = 8.dp))
                StatsCard(contentPadding = 0.dp) {
                    season.sessionLines.forEachIndexed { i, line ->
                        TrainingSessionRow(line)
                        if (i < season.sessionLines.lastIndex) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingSessionRow(line: PlayerTrainingSessionLine) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(weekdayDateLabel(line.date), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = DeepCarbon)
            Spacer(Modifier.height(2.dp))
            val status = when (line.rating) {
                null -> stringResource(Res.string.player_training_not_rated)
                0.0 -> stringResource(Res.string.label_absent)
                else -> null
            }
            Text(
                listOfNotNull(line.startTime.toString().take(5), status).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = StatsSecondaryText
            )
        }
        Spacer(Modifier.width(11.dp))
        val rating = line.rating
        if (rating != null && rating > 0.0) {
            RatingPill(rating, ratingText(rating), minWidth = 40.dp, height = 32.dp, fontSize = 15.sp, corner = 9.dp)
        } else {
            // Absent counts as 0 in the Full stats ranking; here it reads as a missed session, not a score.
            Box(Modifier.size(width = 40.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                Text(if (rating == 0.0) "0" else "—", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = StatsSecondaryText)
            }
        }
    }
}
