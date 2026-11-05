package com.ricardofrango.squadify.presentation.ui.fullstats

import com.ricardofrango.squadify.presentation.ui.components.labelRes
import com.ricardofrango.squadify.presentation.ui.components.StatsNotInPlanMessage
import com.ricardofrango.squadify.presentation.ui.components.squadifyDatePickerColors
import androidx.compose.ui.draw.clip
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.components.RatingInfoButton
import com.ricardofrango.squadify.presentation.ui.components.SectionBorder
import com.ricardofrango.squadify.presentation.ui.components.SectionShape
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.FullStatsColumn
import com.ricardofrango.squadify.model.FullStatsData
import com.ricardofrango.squadify.model.FullStatsLine
import com.ricardofrango.squadify.model.FullStatsMatch
import com.ricardofrango.squadify.model.FullStatsRow
import com.ricardofrango.squadify.model.FullStatsTeam
import com.ricardofrango.squadify.model.FullStatsTraining
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.PlayerPosition
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.TeamSeasonPlayer
import com.ricardofrango.squadify.model.TrainingRating
import com.ricardofrango.squadify.model.sortedBy
import com.ricardofrango.squadify.model.table
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import com.ricardofrango.squadify.presentation.ui.components.PersonPhoto
import com.ricardofrango.squadify.presentation.ui.components.PersonPlaceholder
import com.ricardofrango.squadify.presentation.ui.components.RatingPill
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.initialsOf
import com.ricardofrango.squadify.presentation.ui.components.twoDecimals
import com.ricardofrango.squadify.presentation.ui.components.shortDateLabel
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.*
import kotlin.time.Instant
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Full stats: every player's stats in one table, for a team (only the players who played for it) or a
 * whole age group (its pool, optionally one team). The design is the "Squadify Full Stats" canvas.
 * The player column is pinned and the header row sticky while the stats scroll sideways; tapping a
 * header sorts, tapping a row opens the player. Exactly one of [teamId] / [ageGroupId] is set.
 */
@Composable
fun FullStatsScreen(
    teamId: String?,
    ageGroupId: String?,
    onNavigateBack: () -> Unit,
    onOpenPlayer: (playerId: String, seasonId: String) -> Unit,
    viewModel: FullStatsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state = viewModel.state.collectSettledState()
        LaunchedEffect(teamId, ageGroupId) { viewModel.load(teamId, ageGroupId) }
        FullStatsContent(
            state = state,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.load(teamId, ageGroupId, force = true) },
            onPeriodSelected = viewModel::onPeriodSelected,
            onCustomRange = viewModel::onCustomRange,
            onTypeSelected = viewModel::onTypeSelected,
            onTeamSelected = viewModel::onTeamSelected,
            onPositionSelected = viewModel::onPositionSelected,
            onSort = viewModel::onSort,
            onToggleColumn = viewModel::onToggleColumn,
            onOpenPlayer = { playerId -> (state as? FullStatsUiState.Loaded)?.let { onOpenPlayer(playerId, it.data.seasonId) } }
        )
    }
}

internal enum class FullStatsSheet { NONE, COLUMNS, PERIOD, CUSTOM, TYPE, TEAM, POSITION, EXPLAIN }

@Composable
private fun FullStatsContent(
    state: FullStatsUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onPeriodSelected: (StatsPeriod) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
    onTypeSelected: (StatsFilter) -> Unit,
    onTeamSelected: (String?) -> Unit,
    onPositionSelected: (PlayerPosition?) -> Unit,
    onSort: (FullStatsColumn?) -> Unit,
    onToggleColumn: (FullStatsColumn) -> Unit,
    onOpenPlayer: (String) -> Unit,
    initialSheet: FullStatsSheet = FullStatsSheet.NONE
) {
    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    val loaded = state as? FullStatsUiState.Loaded
    // A long press on a header names its column ("V" -> "Victories"), in a short toast.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showColumnName: (String) -> Unit = { name ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(name, duration = SnackbarDuration.Short)
        }
    }
    Scaffold(
        containerColor = ScreenBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> ColumnNameToast(data.visuals.message) } },
        topBar = {
            SquadifyTopBar(
                // "Infantis B stats" / "Estatísticas de Infantis B"; the plain title while loading.
                title = loaded?.data?.title?.let { stringResource(Res.string.fs_title_of, it) } ?: stringResource(Res.string.fs_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    if (loaded != null) {
                        TextButton(onClick = { sheet = FullStatsSheet.COLUMNS }) {
                            ColumnsIcon()
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(Res.string.fs_columns), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (state) {
            FullStatsUiState.Loading -> LoadingSkeleton(modifier)
            FullStatsUiState.Error -> CenteredMessage(
                modifier,
                title = stringResource(Res.string.fs_error_title),
                body = stringResource(Res.string.msg_check_connection),
                action = stringResource(Res.string.btn_retry) to onRetry,
                filledAction = true,
                error = true
            )
            FullStatsUiState.NotInPlan -> Column(modifier.padding(16.dp)) { StatsNotInPlanMessage() }
            is FullStatsUiState.Loaded -> BoxWithConstraints(modifier) {
                // The side panel is only for a phone lying down, where the filters on top would leave the
                // table a few rows. An unfolded foldable or a tablet has the height: filters on top, as upright.
                val landscape = maxWidth > maxHeight && maxHeight < 480.dp
                val table = remember(state.data, state.query) { state.data.table(state.query) }
                val rows = remember(table, state.sort, state.descending) { table.rows.sortedBy(state.sort, state.descending) }
                val filters = @Composable { m: Modifier ->
                    Filters(state, onOpen = { sheet = it }, stacked = landscape, modifier = m)
                }
                val body = @Composable { m: Modifier ->
                    Column(m) {
                        if (table.isEmpty) {
                            CenteredMessage(
                                Modifier.fillMaxSize(),
                                title = stringResource(Res.string.fs_empty_title),
                                body = stringResource(Res.string.fs_empty_hint),
                                action = stringResource(Res.string.fs_widen) to { sheet = FullStatsSheet.PERIOD }
                            )
                        } else {
                            StatsTable(
                                rows = rows,
                                columns = state.columns,
                                sort = state.sort,
                                descending = state.descending,
                                onSort = onSort,
                                onExplain = { sheet = FullStatsSheet.EXPLAIN },
                                onLongPress = showColumnName,
                                onOpenPlayer = onOpenPlayer,
                                // A white section on the grey, like every screen's content. As tall as its
                                // rows, up to the space left, past which the rows scroll.
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = if (landscape) ScreenPadding.Top else 0.dp, bottom = ScreenPadding.Bottom)
                                    .clip(SectionShape)
                                    .border(SectionBorder, SectionShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
                if (landscape) {
                    Row(Modifier.fillMaxSize()) {
                        filters(Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState()).rightHairline().padding(16.dp))
                        body(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        filters(Modifier.fillMaxWidth())
                        body(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (loaded != null) {
        val close = { sheet = FullStatsSheet.NONE }
        when (sheet) {
            FullStatsSheet.COLUMNS -> ColumnsSheet(loaded, onToggleColumn, close)
            FullStatsSheet.PERIOD -> OptionsSheet(
                title = stringResource(Res.string.fs_period),
                options = StatsPeriod.entries,
                selected = loaded.period,
                label = { stringResource(it.labelRes()) },
                onSelected = { period ->
                    if (period == StatsPeriod.CUSTOM) sheet = FullStatsSheet.CUSTOM else {
                        onPeriodSelected(period)
                        close()
                    }
                },
                onDismiss = close
            )
            FullStatsSheet.CUSTOM -> CustomRangeSheet(
                state = loaded,
                onApply = { from, to ->
                    onCustomRange(from, to)
                    close()
                },
                onDismiss = close
            )
            FullStatsSheet.TYPE -> OptionsSheet(
                title = stringResource(Res.string.fs_type),
                options = StatsFilter.entries,
                selected = loaded.type,
                label = { typeLabel(it) },
                onSelected = {
                    onTypeSelected(it)
                    close()
                },
                onDismiss = close
            )
            FullStatsSheet.TEAM -> OptionsSheet(
                title = stringResource(Res.string.fs_team),
                options = listOf<String?>(null) + loaded.data.teams.map { it.id },
                selected = loaded.teamId,
                label = { teamLabel(loaded, it) },
                onSelected = {
                    onTeamSelected(it)
                    close()
                },
                onDismiss = close
            )
            FullStatsSheet.POSITION -> OptionsSheet(
                title = stringResource(Res.string.fs_col_position),
                options = listOf<PlayerPosition?>(null) + PlayerPosition.entries,
                selected = loaded.position,
                label = { positionLabel(it) },
                onSelected = {
                    onPositionSelected(it)
                    close()
                },
                onDismiss = close
            )
            FullStatsSheet.EXPLAIN -> ExplainSheet(close)
            FullStatsSheet.NONE -> Unit
        }
    }
}

// ---- Filters ----

/**
 * One chip per filter, each opening its sheet: the period, the match type, the team (when the age group
 * has teams) and the position. A row that scrolls sideways, edge to edge; one under the other in landscape's side panel.
 */
@Composable
private fun Filters(
    state: FullStatsUiState.Loaded,
    onOpen: (FullStatsSheet) -> Unit,
    stacked: Boolean,
    modifier: Modifier
) {
    val chips = @Composable { chipModifier: Modifier ->
        FilterSheetChip(rangeLabel(state), { onOpen(FullStatsSheet.PERIOD) }, chipModifier, icon = { CalendarIcon(Color.White) })
        FilterSheetChip(typeLabel(state.type), { onOpen(FullStatsSheet.TYPE) }, chipModifier)
        if (state.data.teams.isNotEmpty()) FilterSheetChip(teamLabel(state, state.teamId), { onOpen(FullStatsSheet.TEAM) }, chipModifier)
        FilterSheetChip(positionLabel(state.position), { onOpen(FullStatsSheet.POSITION) }, chipModifier)
    }
    if (stacked) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) { chips(Modifier.fillMaxWidth()) }
    } else {
        Row(
            modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) { chips(Modifier) }
    }
}

@Composable
private fun FilterSheetChip(text: String, onClick: () -> Unit, modifier: Modifier, icon: (@Composable () -> Unit)? = null) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = ClubChrome, modifier = modifier) {
        Row(Modifier.height(36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(6.dp))
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "All games", or the type alone ("Official"). */
@Composable
private fun typeLabel(type: StatsFilter): String =
    if (type == StatsFilter.ALL) stringResource(Res.string.fs_type_all) else stringResource(type.labelRes())

/** The age group by its name, or one of its teams. */
private fun teamLabel(state: FullStatsUiState.Loaded, teamId: String?): String =
    teamId?.let { id -> state.data.teams.firstOrNull { it.id == id }?.name } ?: state.data.title

@Composable
private fun positionLabel(position: PlayerPosition?): String =
    position?.let { stringResource(it.labelRes()) } ?: stringResource(Res.string.fs_position_all)

/** "Whole season", or "19 Oct – Today". */
@Composable
private fun rangeLabel(state: FullStatsUiState.Loaded): String {
    val (from, to) = state.query.let { it.from to it.to }
    if (from == null && to == null) return stringResource(Res.string.fs_period_season)
    val fromText = from?.let { shortDateLabel(it) } ?: shortDateLabel(state.data.seasonStart)
    val toText = if (to == null || to == state.today) stringResource(Res.string.fs_today) else shortDateLabel(to)
    return stringResource(Res.string.fs_range, fromText, toText)
}

// ---- Table ----

// The player column is as wide as its longest name line, between these: never wider than it was with
// the name on one line, so the stats get the room the second line frees.
private val PlayerColumnMinWidth = 96.dp
private val PlayerColumnMaxWidth = 136.dp
// The photo (28), the gap after it (8) and the cell's side padding (10 + 10).
private val PlayerCellChrome = 56.dp
private val HeaderHeight = 44.dp
private val RowHeight = 48.dp
private val TopRowTint = Color(0xFFF1F8F2)
private val ZebraTint = Color(0xFFFAFAFA)
private val Hairline = CarbonGray.copy(alpha = 0.12f)

private fun FullStatsColumn.width(): Dp = when (this) {
    FullStatsColumn.POSITION -> 92.dp
    FullStatsColumn.GOALS, FullStatsColumn.ASSISTS, FullStatsColumn.MINUTES -> 52.dp
    FullStatsColumn.GAMES_RATING, FullStatsColumn.TRAINING_RATING -> 64.dp
    FullStatsColumn.FINAL_RATING -> 70.dp
    FullStatsColumn.WON, FullStatsColumn.DRAWN, FullStatsColumn.LOST -> 40.dp
    FullStatsColumn.YELLOW_CARDS, FullStatsColumn.RED_CARDS -> 44.dp
    FullStatsColumn.SHOTS, FullStatsColumn.SHOTS_ON_GOAL, FullStatsColumn.FOULS -> 56.dp
}

private fun FullStatsColumn.shortRes(): StringResource = when (this) {
    FullStatsColumn.POSITION -> Res.string.fs_col_position
    FullStatsColumn.GOALS -> Res.string.fs_col_goals
    FullStatsColumn.ASSISTS -> Res.string.fs_col_assists
    FullStatsColumn.MINUTES -> Res.string.fs_col_minutes
    FullStatsColumn.GAMES_RATING -> Res.string.fs_col_games_rating
    FullStatsColumn.TRAINING_RATING -> Res.string.fs_col_training_rating
    FullStatsColumn.FINAL_RATING -> Res.string.fs_col_final_rating
    FullStatsColumn.WON -> Res.string.fs_col_won
    FullStatsColumn.DRAWN -> Res.string.fs_col_drawn
    FullStatsColumn.LOST -> Res.string.fs_col_lost
    FullStatsColumn.YELLOW_CARDS -> Res.string.fs_name_yellow
    FullStatsColumn.RED_CARDS -> Res.string.fs_name_red
    FullStatsColumn.SHOTS -> Res.string.fs_col_shots
    FullStatsColumn.SHOTS_ON_GOAL -> Res.string.fs_col_shots_on_goal
    FullStatsColumn.FOULS -> Res.string.fs_col_fouls
}

private fun FullStatsColumn.nameRes(): StringResource = when (this) {
    FullStatsColumn.POSITION -> Res.string.fs_col_position
    FullStatsColumn.GOALS -> Res.string.fs_col_goals
    FullStatsColumn.ASSISTS -> Res.string.fs_col_assists
    FullStatsColumn.MINUTES -> Res.string.fs_name_minutes
    FullStatsColumn.GAMES_RATING -> Res.string.fs_name_games_rating
    FullStatsColumn.TRAINING_RATING -> Res.string.fs_name_training_rating
    FullStatsColumn.FINAL_RATING -> Res.string.fs_name_final_rating
    FullStatsColumn.WON -> Res.string.fs_name_won
    FullStatsColumn.DRAWN -> Res.string.fs_name_drawn
    FullStatsColumn.LOST -> Res.string.fs_name_lost
    FullStatsColumn.YELLOW_CARDS -> Res.string.fs_name_yellow
    FullStatsColumn.RED_CARDS -> Res.string.fs_name_red
    FullStatsColumn.SHOTS -> Res.string.fs_name_shots
    FullStatsColumn.SHOTS_ON_GOAL -> Res.string.fs_name_shots_on_goal
    FullStatsColumn.FOULS -> Res.string.fs_col_fouls
}

/**
 * The player column is pinned; the header row and the body share one horizontal [ScrollState], so the
 * stats scroll sideways together while the names stay put. The top three on the sorted column are tinted.
 */
@Composable
private fun StatsTable(
    rows: List<FullStatsRow>,
    columns: List<FullStatsColumn>,
    sort: FullStatsColumn?,
    descending: Boolean,
    onSort: (FullStatsColumn?) -> Unit,
    onExplain: () -> Unit,
    onLongPress: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    modifier: Modifier
) {
    val horizontal = rememberScrollState()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val nameStyle = NameStyle
    val headerStyle = HeaderStyle
    val header = stringResource(Res.string.fs_player) + " ↓"
    val playerColumnWidth = remember(rows, header, density) {
        val names = rows.flatMap { splitName(it.player.name).toList().filterNotNull() }
        val widest = names.maxOfOrNull { measurer.measure(it, nameStyle).size.width } ?: 0
        val headerWidth = measurer.measure(header, headerStyle).size.width
        with(density) { maxOf(widest.toDp() + PlayerCellChrome, headerWidth.toDp() + 20.dp) }.coerceIn(PlayerColumnMinWidth, PlayerColumnMaxWidth)
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.height(HeaderHeight).fillMaxWidth()) {
            PlayerHeaderCell(playerColumnWidth, sorted = sort == null, descending = descending, onClick = { onSort(null) }, onLongPress = onLongPress)
            Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(horizontal)) {
                columns.forEach { column -> HeaderCell(column, sorted = column == sort, descending, onClick = { onSort(column) }, onLongPress = onLongPress, onExplain = onExplain) }
            }
        }
        HorizontalDivider(color = Hairline)
        // No podium when sorted by name.
        val tintedTop = if (sort == null) 0 else rows.take(3).count { (it.value(sort) ?: 0.0) > 0.0 }
        Row(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(Modifier.width(playerColumnWidth).rightHairline()) {
                rows.forEachIndexed { i, row -> PlayerCell(row.player, rowTint(i, tintedTop)) { onOpenPlayer(row.player.id) } }
            }
            Column(Modifier.weight(1f).horizontalScroll(horizontal)) {
                rows.forEachIndexed { i, row ->
                    Row(
                        Modifier.height(RowHeight).background(rowTint(i, tintedTop)).bottomHairline().clickable { onOpenPlayer(row.player.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        columns.forEach { column -> ValueCell(row, column) }
                    }
                }
            }
        }
    }
}

private fun rowTint(index: Int, tintedTop: Int): Color = when {
    index < tintedTop -> TopRowTint
    index % 2 == 1 -> ZebraTint
    else -> Color.White
}

/** The column-name toast: the match report's dark rounded toast, the name alone. */
@Composable
private fun ColumnNameToast(message: String) {
    Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = CarbonGray) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

private val NameStyle @Composable get() = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 15.sp)

/** "Rodrigo Costa" as "Rodrigo" and "Costa" (the server's name is first name, then surname); one word, no surname. */
internal fun splitName(name: String): Pair<String, String?> {
    val trimmed = name.trim()
    val space = trimmed.indexOf(' ')
    return if (space < 0) trimmed to null else trimmed.substring(0, space) to trimmed.substring(space + 1).trim()
}

private val HeaderStyle @Composable get() = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp, lineHeight = 13.sp)

@Composable
private fun PlayerHeaderCell(width: Dp, sorted: Boolean, descending: Boolean, onClick: () -> Unit, onLongPress: (String) -> Unit) {
    val name = stringResource(Res.string.fs_player)
    val color = if (sorted) PitchGreen else DeepCarbon.copy(alpha = 0.75f)
    Row(
        Modifier
            .width(width)
            .fillMaxHeight()
            .rightHairline()
            .semantics { contentDescription = name; role = Role.Button }
            .combinedClickable(onClick = onClick, onLongClick = { onLongPress(name) })
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = HeaderStyle, color = color)
        if (sorted) Text(if (descending) " ↓" else " ↑", style = HeaderStyle, color = color)
    }
}

@Composable
private fun HeaderCell(column: FullStatsColumn, sorted: Boolean, descending: Boolean, onClick: () -> Unit, onLongPress: (String) -> Unit, onExplain: () -> Unit) {
    val name = stringResource(column.nameRes())
    val arrow = if (!sorted) "" else if (descending) " ↓" else " ↑"
    val color = if (sorted || column == FullStatsColumn.FINAL_RATING) PitchGreen else DeepCarbon.copy(alpha = 0.75f)
    Row(
        Modifier
            .width(column.width())
            .fillMaxHeight()
            .semantics { contentDescription = name; role = Role.Button }
            .combinedClickable(onClick = onClick, onLongClick = { onLongPress(name) }),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (column) {
            FullStatsColumn.YELLOW_CARDS -> CardGlyph(Amber)
            FullStatsColumn.RED_CARDS -> CardGlyph(AlertRed)
            else -> Text(stringResource(column.shortRes()), style = HeaderStyle, color = color, textAlign = TextAlign.Center, maxLines = 1)
        }
        if (arrow.isNotEmpty()) Text(arrow, style = HeaderStyle, color = color)
        if (column == FullStatsColumn.FINAL_RATING) {
            Spacer(Modifier.width(2.dp))
            RatingInfoButton(stringResource(Res.string.fs_final_info), onExplain)
        }
    }
}

@Composable
private fun PlayerCell(player: TeamSeasonPlayer, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.height(RowHeight).fillMaxWidth().background(tint).bottomHairline().clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        player.photoUrl?.let { PersonPhoto(it, initialsOf(player.name, ""), 28.dp, CarbonGray.copy(alpha = 0.08f), DeepCarbon) }
            ?: PersonPlaceholder(28.dp, DeepCarbon.copy(alpha = 0.3f))
        Spacer(Modifier.width(8.dp))
        // The first name on one line and the surname under it, as in a match's Stats; no shirt number,
        // to leave the stats the room.
        val (first, surname) = splitName(player.name)
        Column(Modifier.weight(1f)) {
            Text(first, style = NameStyle, color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
            surname?.let { Text(it, style = NameStyle, color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun ValueCell(row: FullStatsRow, column: FullStatsColumn) {
    Box(Modifier.width(column.width()).fillMaxHeight(), contentAlignment = Alignment.Center) {
        if (column == FullStatsColumn.POSITION) {
            Text(
                row.player.position?.let { stringResource(it.labelRes()) } ?: "—",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.5.sp),
                color = if (row.player.position == null) StatsSecondaryText else DeepCarbon,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        } else if (column.rating) {
            val value = row.value(column)
            val final = column == FullStatsColumn.FINAL_RATING
            if (value == null) {
                Box(
                    Modifier.widthIn(min = 40.dp).height(24.dp).border(1.dp, CarbonGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("—", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = StatsSecondaryText)
                }
            } else {
                RatingPill(value, value.twoDecimals(), minWidth = if (final) 46.dp else 40.dp, height = if (final) 26.dp else 24.dp, fontSize = if (final) 13.sp else 12.sp, corner = 13.dp)
            }
        } else {
            val value = row.value(column)?.toInt() ?: 0
            val color = when {
                column == FullStatsColumn.YELLOW_CARDS && value > 0 -> Color(0xFF7A5200)
                column == FullStatsColumn.RED_CARDS && value > 0 -> AlertRed
                (column == FullStatsColumn.YELLOW_CARDS || column == FullStatsColumn.RED_CARDS) -> StatsSecondaryText
                else -> DeepCarbon
            }
            Text(
                if (column == FullStatsColumn.MINUTES) "$value'" else value.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
                color = color
            )
        }
    }
}

@Composable
private fun CardGlyph(color: Color) {
    Box(Modifier.size(width = 10.dp, height = 13.dp).background(color, RoundedCornerShape(1.5.dp)))
}

private fun Modifier.rightHairline() = drawBehind {
    drawLine(Hairline, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), strokeWidth = 1f)
}

private fun Modifier.bottomHairline() = drawBehind {
    drawLine(Hairline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
}

// ---- Sheets ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullStatsSheetFrame(onDismiss: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            content()
        }
    }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ClubChrome, contentColor = Color.White)
    ) { Text(text) }
}

@Composable
private fun ColumnsSheet(state: FullStatsUiState.Loaded, onToggle: (FullStatsColumn) -> Unit, onDismiss: () -> Unit) {
    FullStatsSheetFrame(onDismiss, stringResource(Res.string.fs_columns_title)) {
        Text(stringResource(Res.string.fs_columns_note), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            FullStatsColumn.entries.filter { !it.rating || state.data.canSeeRatings }.forEach { column ->
                val shown = column !in state.hidden
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clickable { onToggle(column) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(column.nameRes()), style = MaterialTheme.typography.bodyLarge, color = DeepCarbon, modifier = Modifier.weight(1f))
                    Switch(
                        checked = shown,
                        onCheckedChange = { onToggle(column) },
                        colors = SwitchDefaults.colors(checkedTrackColor = PitchGreen, checkedThumbColor = Color.White)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SheetButton(stringResource(Res.string.fs_done), onDismiss)
    }
}

/** A filter's choices, the selected one marked with a green dot; picking one applies it. */
@Composable
private fun <T> OptionsSheet(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onSelected: (T) -> Unit, onDismiss: () -> Unit) {
    FullStatsSheetFrame(onDismiss, title) {
        Spacer(Modifier.height(8.dp))
        options.forEach { option ->
            Row(
                Modifier.fillMaxWidth().height(52.dp).clickable { onSelected(option) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label(option), style = MaterialTheme.typography.bodyLarge, color = DeepCarbon, modifier = Modifier.weight(1f))
                if (option == selected) Box(Modifier.size(10.dp).background(PitchGreen, CircleShape))
            }
        }
    }
}

private fun StatsPeriod.labelRes(): StringResource = when (this) {
    StatsPeriod.THIS_WEEK -> Res.string.fs_period_week
    StatsPeriod.LAST_7_DAYS -> Res.string.fs_period_7_days
    StatsPeriod.THIS_MONTH -> Res.string.fs_period_month
    StatsPeriod.SEASON -> Res.string.fs_period_season
    StatsPeriod.CUSTOM -> Res.string.fs_period_custom
}

/** From / to within the season, up to today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeSheet(state: FullStatsUiState.Loaded, onApply: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    fun LocalDate.millis() = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    fun Long.date() = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
    val first = state.data.seasonStart.millis()
    val last = minOf(state.today, state.data.seasonEnd).millis()
    val (from, to) = state.query.let { it.from to it.to }
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = from?.millis(),
        initialSelectedEndDateMillis = to?.millis(),
        selectableDates = remember(first, last) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in first..last
            }
        }
    )
    FullStatsSheetFrame(onDismiss, stringResource(Res.string.fs_period_custom)) {
        DateRangePicker(state = pickerState, colors = squadifyDatePickerColors(), modifier = Modifier.heightIn(max = 460.dp), title = null, headline = null, showModeToggle = false)
        val start = pickerState.selectedStartDateMillis
        val end = pickerState.selectedEndDateMillis ?: start
        SheetButton(stringResource(Res.string.btn_apply), { if (start != null && end != null) onApply(start.date(), end.date()) }, enabled = start != null)
    }
}

@Composable
private fun ExplainSheet(onDismiss: () -> Unit) {
    FullStatsSheetFrame(onDismiss, stringResource(Res.string.fs_final_title)) {
        Text(
            stringResource(Res.string.fs_final_body),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
            color = DeepCarbon.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            stringResource(Res.string.fs_final_example),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
            color = DeepCarbon,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().background(CarbonGray.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(12.dp)
        )
        Spacer(Modifier.height(14.dp))
        SheetButton(stringResource(Res.string.btn_ok), onDismiss)
    }
}

// ---- States ----

@Composable
private fun CenteredMessage(modifier: Modifier, title: String, body: String, action: Pair<String, () -> Unit>?, filledAction: Boolean = false, error: Boolean = false) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val tint = if (error) AlertRed else PitchGreen
        Box(Modifier.size(56.dp).background(tint.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            if (error) Text("!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = tint)
            else BarsIcon(tint)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        if (action == null) return@Column
        Spacer(Modifier.height(14.dp))
        if (filledAction) {
            Button(onClick = action.second, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = ClubChrome, contentColor = Color.White)) { Text(action.first) }
        } else {
            OutlinedButton(onClick = action.second, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, PitchGreen)) { Text(action.first, color = PitchGreen) }
        }
    }
}

@Composable
private fun LoadingSkeleton(modifier: Modifier) {
    Column(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(180.dp).height(36.dp).background(SkeletonColor, RoundedCornerShape(18.dp)))
            Box(Modifier.fillMaxWidth().height(40.dp).background(SkeletonColor, RoundedCornerShape(12.dp)))
        }
        Box(Modifier.fillMaxWidth().height(HeaderHeight).background(SkeletonColor))
        repeat(8) { Box(Modifier.fillMaxWidth().height(RowHeight).background(if (it % 2 == 0) Color.White else SkeletonColor.copy(alpha = 0.5f)).bottomHairline()) }
        Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PitchGreen, modifier = Modifier.size(24.dp))
        }
    }
}

private val SkeletonColor = Color(0xFFF0F0F0)

// ---- Icons ----

@Composable
private fun ColumnsIcon() {
    Box(Modifier.size(18.dp).drawBehind {
        val s = 2.dp.toPx()
        val w = size.width
        drawRoundRect(Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        drawLine(Color.White, Offset(w / 3, 0f), Offset(w / 3, size.height), s)
        drawLine(Color.White, Offset(2 * w / 3, 0f), Offset(2 * w / 3, size.height), s)
    })
}

@Composable
private fun CalendarIcon(color: Color) {
    Box(Modifier.size(14.dp).drawBehind {
        val s = 1.6.dp.toPx()
        drawRoundRect(color, topLeft = Offset(0f, size.height * 0.15f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        drawLine(color, Offset(0f, size.height * 0.42f), Offset(size.width, size.height * 0.42f), s)
    })
}

@Composable
private fun BarsIcon(color: Color) {
    Box(Modifier.size(26.dp).drawBehind {
        val s = 4.dp.toPx()
        val h = size.height
        listOf(0.2f to 0.55f, 0.5f to 0.2f, 0.8f to 0.4f).forEach { (x, top) ->
            drawLine(color, Offset(size.width * x, h * 0.9f), Offset(size.width * x, h * top), s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    })
}

// ---- Previews ----

internal fun previewFullStats(canSeeRatings: Boolean = true): FullStatsData {
    val names = listOf("Rodrigo Costa", "Tomás Silva", "Afonso Pereira", "Duarte Vilaça", "Miguel Ferreira", "Gonçalo Reis", "Lucas Matos", "João Pinto", "Pedro Santos", "Dinis Carvalho")
    val players = names.mapIndexed { i, n -> TeamSeasonPlayer("p$i", n, shirtNumber = i + 1) }
    val at = Instant.parse("2026-10-18T10:00:00Z")
    val matches = listOf(FullStatsMatch("m1", "A", at, MatchType.CHAMPIONSHIP, 3, 1), FullStatsMatch("m2", "A", at, MatchType.FRIENDLY, 1, 1))
    val lines = players.dropLast(1).flatMapIndexed { i, p ->
        matches.map { m -> FullStatsLine(m.matchId, p.id, true, 60 - i * 4, goals = (i + m.ourScore) % 3, assists = i % 2, yellowCards = if (i == 7) 1 else 0, redCards = 0, rating = 5 + (i * 3 + m.ourScore) % 5) }
    }
    val trainings = (19..21).map { d ->
        FullStatsTraining("t$d", LocalDate(2026, 10, d), players.dropLast(1).mapIndexed { i, p -> TrainingRating(p.id, 5.5 + (i + d) % 4) })
    }
    return FullStatsData(
        title = "Sub-13", ageGroupId = "ag", seasonId = "s", seasonName = "2026/27",
        seasonStart = LocalDate(2026, 9, 1), seasonEnd = LocalDate(2027, 6, 30),
        teams = listOf(FullStatsTeam("A", "Sub-13 A"), FullStatsTeam("B", "Sub-13 B")),
        matches = matches, lines = lines, trainings = trainings, players = players, canSeeRatings = canSeeRatings
    )
}

internal fun previewLoaded(
    data: FullStatsData = previewFullStats(),
    period: StatsPeriod = StatsPeriod.SEASON,
    hidden: Set<FullStatsColumn> = emptySet(),
    teamId: String? = "A"
) =
    FullStatsUiState.Loaded(
        data = data, period = period, customFrom = null, customTo = null, type = StatsFilter.ALL, teamId = teamId, position = null,
        sort = if (data.canSeeRatings) FullStatsColumn.FINAL_RATING else FullStatsColumn.GOALS, descending = true,
        hidden = hidden, today = LocalDate(2026, 10, 24)
    )

@Composable
internal fun PreviewFullStats(state: FullStatsUiState, sheet: FullStatsSheet = FullStatsSheet.NONE) {
    SquadifyTheme {
        FullStatsContent(state, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, initialSheet = sheet)
    }
}

@Preview
@Composable
private fun FullStatsStaffPreview() = PreviewFullStats(previewLoaded())

@Preview
@Composable
private fun FullStatsParentPreview() = PreviewFullStats(previewLoaded(previewFullStats(canSeeRatings = false)))

@Preview
@Composable
private fun FullStatsAgeGroupPreview() = PreviewFullStats(previewLoaded(teamId = null))

@Preview
@Composable
private fun FullStatsEmptyPreview() = PreviewFullStats(previewLoaded(period = StatsPeriod.LAST_7_DAYS).copy(today = LocalDate(2026, 12, 1)))

@Preview
@Composable
private fun FullStatsLoadingPreview() = PreviewFullStats(FullStatsUiState.Loading)

@Preview
@Composable
private fun FullStatsErrorPreview() = PreviewFullStats(FullStatsUiState.Error)

@Preview
@Composable
private fun FullStatsNotInPlanPreview() = PreviewFullStats(FullStatsUiState.NotInPlan)
