package com.ricardofrango.squadify.presentation.ui.training

import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.model.TrainingRating
import com.ricardofrango.squadify.model.TrainingRatings
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.FilterChipRow
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.PinnedBottomBar
import com.ricardofrango.squadify.presentation.ui.matchdetails.CallUpSort
import com.ricardofrango.squadify.presentation.ui.matchdetails.playerComparator
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.PlayerBadge
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_save_ratings
import squadify.app.shared.generated.resources.btn_saving_changes
import squadify.app.shared.generated.resources.label_absent
import squadify.app.shared.generated.resources.label_absent_count
import squadify.app.shared.generated.resources.label_rated_count
import squadify.app.shared.generated.resources.label_position_field
import squadify.app.shared.generated.resources.label_sort
import squadify.app.shared.generated.resources.label_sort_name
import squadify.app.shared.generated.resources.label_training_subtitle
import squadify.app.shared.generated.resources.msg_rating_invalid
import squadify.app.shared.generated.resources.msg_ratings_not_open
import squadify.app.shared.generated.resources.title_rate_players

/**
 * Rate players: every player of the pool, sorted by name (default) or position, each with a text box for
 * a rating from 0 to 10 with up to two decimals. 0 means absent ("did not take part"); empty means not
 * rated. Save, pinned at the bottom under the rated count, is enabled after a change while every box is
 * valid, and saves partially; leaving with changes asks first.
 */
@Composable
fun RatePlayersScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RatePlayersViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(sessionId) { viewModel.start(sessionId) }
        LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved() } }
        RatePlayersContent(
            state = state,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.start(sessionId, force = true) },
            onText = viewModel::onText,
            onSort = viewModel::onSort,
            onSave = viewModel::save
        )
    }
}

@Composable
private fun RatePlayersContent(
    state: RatePlayersUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onText: (String, String) -> Unit,
    onSort: (CallUpSort) -> Unit,
    onSave: () -> Unit
) {
    var askDiscard by remember { mutableStateOf(false) }
    val leave = { if (state.dirty) askDiscard = true else onNavigateBack() }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = state.dirty, onBackCompleted = { askDiscard = true })
    val ratings = state.ratings
    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            SquadifyTopBar(
                title = stringResource(Res.string.title_rate_players),
                subtitle = ratings?.let { stringResource(Res.string.label_training_subtitle, sessionTitle(it.session.date), it.ageGroupName) },
                onNavigateBack = leave
            )
        },
        bottomBar = {
            if (ratings != null) {
                PinnedBottomBar {
                    if (!ratings.ratingsOpen) Text(stringResource(Res.string.msg_ratings_not_open), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
                    // 12dp to the button (the bar's 8dp gap + 4), the same as the bar's padding under it.
                    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.label_rated_count, state.ratedCount, ratings.players.size), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
                        if (state.absentCount > 0) {
                            Text(" · " + stringResource(Res.string.label_absent_count, state.absentCount), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
                        }
                    }
                    // Only when there's an error: an empty error line would widen the gap to the button.
                    if (state.error != null) FormErrorLine(state.error)
                    PrimaryLoadingButton(
                        text = stringResource(Res.string.btn_save_ratings),
                        submittingText = stringResource(Res.string.btn_saving_changes),
                        isLoading = state.saving,
                        enabled = state.canSave,
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).background(ScreenBackground)
        if (ratings == null) {
            PlanLoadingOrError(state.loading, modifier, onRetry)
            return@Scaffold
        }
        val players = ratings.players.sortedWith(playerComparator(state.sort))
        // No bottom padding: the list runs straight into the pinned bar.
        LazyColumn(
            modifier,
            contentPadding = PaddingValues(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = ScreenPadding.Top),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChipRow(
                    options = listOf(CallUpSort.NAME, CallUpSort.POSITION),
                    selected = state.sort,
                    label = { stringResource(if (it == CallUpSort.POSITION) Res.string.label_position_field else Res.string.label_sort_name) },
                    onSelect = onSort,
                    insidePaddedContent = true,
                    leadingLabel = stringResource(Res.string.label_sort)
                )
            }
            itemsIndexed(players, key = { _, p -> p.id }) { i, player ->
                PlayerRatingRow(
                    player = player,
                    text = state.texts[player.id].orEmpty(),
                    rating = state.current[player.id],
                    invalid = player.id in state.invalid,
                    enabled = ratings.ratingsOpen && !state.saving,
                    last = i == players.lastIndex,
                    onText = { onText(player.id, it) }
                )
            }
            // As tall as the keyboard while it's open (nothing when it's closed), so the last players
            // can scroll up above it.
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime)) }
        }
    }
    if (askDiscard) DiscardChangesDialog(onDiscard = { askDiscard = false; onNavigateBack() }, onKeepEditing = { askDiscard = false })
}

@Composable
private fun PlayerRatingRow(
    player: Player,
    text: String,
    rating: Double?,
    invalid: Boolean,
    enabled: Boolean,
    last: Boolean,
    onText: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerBadge(player.photoUrl, player.shirtNumber)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${player.firstName} ${player.lastName}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = DeepCarbon)
                // Only an invalid box or Absent gets a second line; otherwise the name sits centred in the row.
                val status = when {
                    invalid -> stringResource(Res.string.msg_rating_invalid)
                    rating == 0.0 -> stringResource(Res.string.label_absent)
                    else -> null
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (invalid) AlertRed else StatsSecondaryText) }
            }
            Spacer(Modifier.width(10.dp))
            FormTextField(
                value = text,
                onValueChange = onText,
                label = null,
                placeholder = "0–10",
                enabled = enabled,
                keyboardType = KeyboardType.Decimal,
                isError = invalid,
                // Next moves to the following player's box; the last one closes the keyboard.
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
                // Four characters at most ("7,55"), centred.
                textAlign = TextAlign.Center,
                modifier = Modifier.width(76.dp)
            )
        }
    }
}

// ---- Previews ----

private val previewRatings = TrainingRatings(
    session = TrainingSession("s1", "ag1", LocalDate(2026, 10, 8), LocalTime(18, 30), LocalTime(20, 0)),
    ageGroupName = "Sub-15",
    clubName = "CD Ribamar",
    players = listOf(
        Player("p1", "c1", "Tomás", "Bragança", LocalDate(2012, 1, 1), shirtNumber = 10, active = true, position = PlayerPosition.STRIKER),
        Player("p2", "c1", "Gil", "Bettencourt", LocalDate(2012, 1, 1), shirtNumber = 6, active = true, position = PlayerPosition.MIDFIELD),
        Player("p3", "c1", "Isaac", "Mourão", LocalDate(2012, 1, 1), shirtNumber = 3, active = true, position = PlayerPosition.DEFENDER)
    ),
    ratings = listOf(TrainingRating("p1", 8.4), TrainingRating("p2", 0.0)),
    ratingsOpen = true
)

@Preview
@Composable
private fun RatePlayersPreview() {
    val values = mapOf("p1" to 8.4, "p2" to 0.0, "p3" to null)
    SquadifyTheme {
        RatePlayersContent(RatePlayersUiState(loading = false, ratings = previewRatings, original = values, texts = mapOf("p1" to "8.45", "p2" to "0", "p3" to "11")), {}, {}, { _, _ -> }, {}, {})
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun RatePlayersLandscapePreview() {
    val values = mapOf("p1" to 8.4, "p2" to 0.0, "p3" to null)
    SquadifyTheme {
        RatePlayersContent(RatePlayersUiState(loading = false, ratings = previewRatings, original = values, texts = mapOf("p1" to "8.45", "p2" to "0", "p3" to "11")), {}, {}, { _, _ -> }, {}, {})
    }
}
