package com.ricardofrango.squadify.presentation.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.TrainingRating
import com.ricardofrango.squadify.model.TrainingRatings
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_absent
import squadify.app.shared.generated.resources.btn_save_ratings
import squadify.app.shared.generated.resources.btn_saving_changes
import squadify.app.shared.generated.resources.cd_decrease
import squadify.app.shared.generated.resources.cd_increase
import squadify.app.shared.generated.resources.label_absent
import squadify.app.shared.generated.resources.label_absent_count
import squadify.app.shared.generated.resources.label_not_rated
import squadify.app.shared.generated.resources.label_rated_count
import squadify.app.shared.generated.resources.label_training_subtitle
import squadify.app.shared.generated.resources.msg_rate_intro
import squadify.app.shared.generated.resources.msg_ratings_not_open
import squadify.app.shared.generated.resources.title_rate_players

/**
 * Rate players (design 39g): every player of the pool by shirt number. Tap a row, then a quick 5–9 (the
 * next unrated player opens), − / + by 0.1, or Absent (0, "did not take part"). The match report's colour
 * bands. Save is enabled after any change and saves partially; leaving with changes asks first.
 */
@Composable
fun RatePlayersScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RatePlayersViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(sessionId) { viewModel.start(sessionId) }
    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved() } }
    RatePlayersContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.start(sessionId, force = true) },
        onOpen = viewModel::open,
        onSetAndNext = viewModel::setAndNext,
        onStep = viewModel::step,
        onSave = viewModel::save
    )
}

@Composable
private fun RatePlayersContent(
    state: RatePlayersUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onSetAndNext: (String, Double) -> Unit,
    onStep: (String, Int) -> Unit,
    onSave: () -> Unit
) {
    var askDiscard by remember { mutableStateOf(false) }
    val leave = { if (state.dirty) askDiscard = true else onNavigateBack() }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = state.dirty, onBackCompleted = { askDiscard = true })
    val ratings = state.ratings
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.title_rate_players),
                subtitle = ratings?.let { stringResource(Res.string.label_training_subtitle, sessionTitle(it.session.date), it.ageGroupName) },
                onNavigateBack = leave
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).background(Color.White)
        if (ratings == null) {
            PlanLoadingOrError(state.loading, modifier, onRetry)
            return@Scaffold
        }
        Column(modifier) {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text(
                        stringResource(if (ratings.ratingsOpen) Res.string.msg_rate_intro else Res.string.msg_ratings_not_open),
                        style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText, modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                items(ratings.players, key = { it.id }) { player ->
                    PlayerRatingRow(
                        player = player,
                        rating = state.current[player.id],
                        open = state.open == player.id && ratings.ratingsOpen,
                        enabled = ratings.ratingsOpen && !state.saving,
                        onOpen = { onOpen(player.id) },
                        onSetAndNext = { onSetAndNext(player.id, it) },
                        onStep = { onStep(player.id, it) }
                    )
                }
            }
            FormErrorLine(state.error)
            Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.label_rated_count, state.ratedCount, ratings.players.size), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                        if (state.absentCount > 0) Text(stringResource(Res.string.label_absent_count, state.absentCount), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
                    }
                    PrimaryLoadingButton(
                        text = stringResource(Res.string.btn_save_ratings),
                        submittingText = stringResource(Res.string.btn_saving_changes),
                        isLoading = state.saving,
                        enabled = state.dirty && !state.saving,
                        onClick = onSave,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                }
            }
        }
    }
    if (askDiscard) DiscardChangesDialog(onDiscard = { askDiscard = false; onNavigateBack() }, onKeepEditing = { askDiscard = false })
}

@Composable
private fun PlayerRatingRow(
    player: Player,
    rating: Double?,
    open: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onSetAndNext: (Double) -> Unit,
    onStep: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (open) PitchGreen.copy(alpha = 0.05f) else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (open) PitchGreen else CarbonGray.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.clickable(enabled = enabled, onClick = onOpen).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(CarbonGray.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(player.shirtNumber?.toString() ?: "${player.firstName.take(1)}${player.lastName.take(1)}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${player.firstName} ${player.lastName}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = DeepCarbon)
                    Text(
                        when (rating) {
                            null -> stringResource(Res.string.label_not_rated)
                            0.0 -> stringResource(Res.string.label_absent)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText
                    )
                }
                RatingBadge(rating)
            }
            if (open) {
                Spacer(Modifier.width(0.dp))
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (5..9).forEach { n ->
                        OutlinedButton(onClick = { onSetAndNext(n.toDouble()) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.widthIn(min = 44.dp)) {
                            Text("$n", color = DeepCarbon, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onStep(-1) }) { Text("−", style = MaterialTheme.typography.titleLarge, color = DeepCarbon, modifier = Modifier.padding(horizontal = 6.dp)) }
                    Text(rating?.takeIf { it > 0 }?.oneDecimalString() ?: "—", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                    TextButton(onClick = { onStep(1) }) { Text("+", style = MaterialTheme.typography.titleLarge, color = DeepCarbon, modifier = Modifier.padding(horizontal = 6.dp)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onSetAndNext(0.0) }) { Text(stringResource(Res.string.btn_absent), color = DeepCarbon.copy(alpha = 0.7f), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** The rating in its colour band, "—" when not rated. */
@Composable
private fun RatingBadge(rating: Double?) {
    val text = when (rating) {
        null -> "—"
        0.0 -> stringResource(Res.string.btn_absent)
        else -> rating.oneDecimalString()
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
        color = if (rating == null) DeepCarbon.copy(alpha = 0.4f) else Color.White,
        modifier = Modifier
            .background(if (rating == null) CarbonGray.copy(alpha = 0.06f) else ratingColor(rating), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

// ---- Previews ----

private val previewRatings = TrainingRatings(
    session = TrainingSession("s1", "ag1", LocalDate(2026, 10, 8), LocalTime(18, 30), LocalTime(20, 0)),
    ageGroupName = "Sub-15",
    clubName = "CD Ribamar",
    players = listOf(
        Player("p1", "c1", "Tomás", "Bragança", LocalDate(2012, 1, 1), shirtNumber = 10, active = true),
        Player("p2", "c1", "Gil", "Bettencourt", LocalDate(2012, 1, 1), shirtNumber = 6, active = true),
        Player("p3", "c1", "Isaac", "Mourão", LocalDate(2012, 1, 1), shirtNumber = 3, active = true)
    ),
    ratings = listOf(TrainingRating("p1", 8.4), TrainingRating("p2", 0.0)),
    ratingsOpen = true
)

@Preview
@Composable
private fun RatePlayersPreview() {
    val values = mapOf("p1" to 8.4, "p2" to 0.0, "p3" to null)
    SquadifyTheme {
        RatePlayersContent(RatePlayersUiState(loading = false, ratings = previewRatings, original = values, current = values, open = "p3"), {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun RatePlayersLandscapePreview() {
    val values = mapOf("p1" to 8.4, "p2" to 0.0, "p3" to null)
    SquadifyTheme {
        RatePlayersContent(RatePlayersUiState(loading = false, ratings = previewRatings, original = values, current = values, open = "p3"), {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
    }
}
