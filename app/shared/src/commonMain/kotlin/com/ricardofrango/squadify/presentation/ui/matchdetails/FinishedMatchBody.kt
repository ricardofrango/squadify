package com.ricardofrango.squadify.presentation.ui.matchdetails

import com.ricardofrango.squadify.presentation.ui.components.StatsNotInPlanMessage
import com.ricardofrango.squadify.presentation.ui.components.SegmentedFilter
import com.ricardofrango.squadify.presentation.ui.components.StatsMessage
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import androidx.compose.runtime.CompositionLocalProvider
import com.ricardofrango.squadify.presentation.ui.livematch.LocalPitchOnLight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.TimelineChange
import com.ricardofrango.squadify.model.TimelineEntry
import com.ricardofrango.squadify.model.formationText
import com.ricardofrango.squadify.model.matchTimeline
import com.ricardofrango.squadify.presentation.ui.components.PersonPhoto
import com.ricardofrango.squadify.presentation.ui.components.PersonPlaceholder
import com.ricardofrango.squadify.presentation.ui.components.RatingPill
import com.ricardofrango.squadify.presentation.ui.components.DetailBottomBar
import com.ricardofrango.squadify.presentation.ui.components.DetailRail
import com.ricardofrango.squadify.presentation.ui.components.DetailTab
import com.ricardofrango.squadify.presentation.ui.livematch.DisplayEvent
import com.ricardofrango.squadify.presentation.ui.livematch.PitchPanel
import com.ricardofrango.squadify.presentation.ui.livematch.PitchTokenInfo
import com.ricardofrango.squadify.presentation.ui.livematch.teamSize
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.IsClubThemed
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*
import kotlin.math.roundToInt

// A finished match on Match Details (TECHNICAL_DECISIONS.md §66): tabs in the bottom bar instead of
// the call-up. The staff (the report's readers) get General (the report and the details), Stats (each
// player's line), Events (filterable) and Timeline (the pitch at every change, with a slider through
// the game). Anyone else gets General with the details only, and Events with the goals only. The logic behind these is in FinishedMatchRecord.kt.

private val Hairline = CarbonGray.copy(alpha = 0.12f)
private val RowHeight = 48.dp
private val StatColumnWidth = 36.dp
private val RatingColumnWidth = 48.dp
private val HeaderHeight = 40.dp
// Past this, a long first name or surname is cut with "…", so the stats always have room beside it.
private val PlayerColumnMaxWidth = 210.dp

private fun FinishedTab.detailTab() = when (this) {
    FinishedTab.GENERAL -> DetailTab(Res.drawable.ic_info, Res.string.md_tab_general)
    FinishedTab.STATS -> DetailTab(Res.drawable.ic_stats, Res.string.md_tab_stats)
    FinishedTab.EVENTS -> DetailTab(Res.drawable.ic_events, Res.string.md_tab_events)
    FinishedTab.TIMELINE -> DetailTab(Res.drawable.ic_timeline, Res.string.md_tab_timeline)
}

/** The tabs in the bottom bar, as on Team and Age Group details. */
@Composable
internal fun FinishedBottomBar(tabs: List<FinishedTab>, selected: FinishedTab, onSelect: (FinishedTab) -> Unit) =
    DetailBottomBar(tabs.map { it.detailTab() }, tabs.indexOf(selected)) { onSelect(tabs[it]) }

/** Landscape: the same tabs as a rail on the left. */
@Composable
internal fun FinishedRail(tabs: List<FinishedTab>, selected: FinishedTab, onSelect: (FinishedTab) -> Unit) =
    DetailRail(tabs.map { it.detailTab() }, tabs.indexOf(selected)) { onSelect(tabs[it]) }

/** A finished match's body: the selected tab. Anyone but the staff only has General and Events, both cut down. */
@Composable
internal fun FinishedMatchBody(
    state: MatchDetailsUiState.Idle,
    tab: FinishedTab,
    modifier: Modifier,
    onOpenReport: (edit: Boolean) -> Unit
) {
    val details = state.details
    when (tab.takeIf { it in finishedTabs(details.canSeeMatchRecord, !details.match.enteredAfterGame) } ?: FinishedTab.GENERAL) {
        FinishedTab.GENERAL -> GeneralTab(state, modifier, onOpenReport)
        FinishedTab.STATS -> StatsTab(state, modifier)
        FinishedTab.EVENTS -> EventsTab(details, modifier)
        FinishedTab.TIMELINE -> TimelineTab(details, modifier)
    }
}

// ---- General ----

/** The report card (staff only, first) and the match's details. */
@Composable
private fun GeneralTab(state: MatchDetailsUiState.Idle, modifier: Modifier, onOpenReport: (edit: Boolean) -> Unit) {
    val details = state.details
    LazyColumn(modifier, contentPadding = ScreenPadding.Values, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.transientError?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } }
        details.report?.let { item { MatchReportCard(it, onOpenReport) } }
        // No gap above the title when it's the first thing on the tab (no report card for a member).
        item { SectionHeader(stringResource(Res.string.label_details_section), first = state.transientError == null && details.report == null) }
        item { DetailsCard(details, onSetType = null) }
    }
}

// ---- Stats ----

@Composable
private fun StatsTab(state: MatchDetailsUiState.Idle, modifier: Modifier) {
    val details = state.details
    val lines = remember(details) { matchStatLines(details) }
    // Sorted by a column (highest first) or, with none, by name; tapping a header picks it.
    var sort by rememberSaveable(details.match.id) { mutableStateOf<MatchStatColumn?>(defaultStatSort(lines.played)) }
    val rows = remember(lines, sort) { lines.played.sortedByStat(sort) }
    // A long press on a header names its column, as on Full stats.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showColumnName: (String) -> Unit = { name ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(name, duration = SnackbarDuration.Short)
        }
    }

    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding.Values, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionHeader(stringResource(Res.string.md_players), first = true) }
            if (!details.statsInPlan) {
                item { StatsNotInPlanMessage() }
            } else if (rows.isEmpty()) {
                item { Text(stringResource(Res.string.md_no_stats), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.5f)) }
            } else {
                item { StatsTable(rows, sort, onSort = { sort = it }, onLongPress = showColumnName) }
            }
            if (lines.unused.isNotEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.md_unused, lines.unused.joinToString(", ") { "${it.firstName} ${it.lastName}" }),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.55f)
                    )
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter)) { data ->
            Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = CarbonGray) {
                Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
            }
        }
    }
}

private fun MatchStatColumn.shortRes(): StringResource? = when (this) {
    MatchStatColumn.RATING -> Res.string.md_col_rating
    MatchStatColumn.MINUTES -> Res.string.fs_col_minutes
    MatchStatColumn.GOALS -> Res.string.md_col_goals
    MatchStatColumn.ASSISTS -> Res.string.md_col_assists
    // The cards are drawn, not written.
    MatchStatColumn.YELLOW, MatchStatColumn.RED -> null
}

private fun MatchStatColumn.nameRes(): StringResource = when (this) {
    MatchStatColumn.RATING -> Res.string.md_name_rating
    MatchStatColumn.MINUTES -> Res.string.fs_name_minutes
    MatchStatColumn.GOALS -> Res.string.md_name_goals
    MatchStatColumn.ASSISTS -> Res.string.md_name_assists
    MatchStatColumn.YELLOW -> Res.string.fs_name_yellow
    MatchStatColumn.RED -> Res.string.fs_name_red
}

private fun MatchStatColumn.width(): Dp = if (this == MatchStatColumn.RATING) RatingColumnWidth else StatColumnWidth

/**
 * The player column is pinned and as wide as the longest first name or surname (each on its own line,
 * up to [PlayerColumnMaxWidth]); the stat columns scroll sideways, header and rows together.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatsTable(rows: List<MatchStatLine>, sort: MatchStatColumn?, onSort: (MatchStatColumn?) -> Unit, onLongPress: (String) -> Unit) {
    val headerStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
    val horizontal = rememberScrollState()
    fun Modifier.rowLine(i: Int) = if (i < rows.lastIndex) bottomHairline() else this
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))) {
        Row {
            Column(Modifier.width(IntrinsicSize.Max).widthIn(max = PlayerColumnMaxWidth).rightHairline()) {
                val playerName = stringResource(Res.string.fs_player)
                Text(
                    playerName + if (sort == null) " ↑" else "",
                    style = headerStyle,
                    color = if (sort == null) PitchGreen else DeepCarbon.copy(alpha = 0.75f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HeaderHeight)
                        .bottomHairline()
                        .semantics { contentDescription = playerName; role = Role.Button }
                        .combinedClickable(onClick = { onSort(null) }, onLongClick = { onLongPress(playerName) })
                        .padding(horizontal = 12.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
                rows.forEachIndexed { i, line -> PlayerCell(line.player, Modifier.fillMaxWidth().height(RowHeight).rowLine(i)) }
            }
            Column(Modifier.weight(1f).horizontalScroll(horizontal)) {
                Row(Modifier.height(HeaderHeight).bottomHairline(), verticalAlignment = Alignment.CenterVertically) {
                    MatchStatColumn.entries.forEach { column ->
                        val name = stringResource(column.nameRes())
                        val sorted = column == sort
                        val color = if (sorted) PitchGreen else DeepCarbon.copy(alpha = 0.75f)
                        Row(
                            Modifier
                                .width(column.width())
                                .fillMaxHeight()
                                .semantics { contentDescription = name; role = Role.Button }
                                .combinedClickable(onClick = { onSort(column) }, onLongClick = { onLongPress(name) }),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (column) {
                                MatchStatColumn.YELLOW -> CardGlyph(Amber)
                                MatchStatColumn.RED -> CardGlyph(LossRed)
                                else -> Text(stringResource(column.shortRes()!!), style = headerStyle, color = color, maxLines = 1)
                            }
                            if (sorted) Text("↓", style = headerStyle, color = color)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                rows.forEachIndexed { i, line ->
                    Row(Modifier.height(RowHeight).rowLine(i), verticalAlignment = Alignment.CenterVertically) {
                        MatchStatColumn.entries.forEach { column -> ValueCell(line, column) }
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerCell(player: Player, modifier: Modifier) {
    Row(modifier.padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        player.photoUrl?.let { PersonPhoto(it, "${player.firstName.take(1)}${player.lastName.take(1)}", 28.dp, CarbonGray.copy(alpha = 0.08f), DeepCarbon) }
            ?: PersonPlaceholder(28.dp, DeepCarbon.copy(alpha = 0.3f))
        Spacer(Modifier.width(8.dp))
        // The first name on one line and the surname under it (one line with no surname); no shirt
        // number, to leave the name the room.
        val nameStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 15.sp)
        Column {
            Text(player.firstName.trim(), style = nameStyle, color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
            player.lastName.trim().takeIf { it.isNotEmpty() }?.let {
                Text(it, style = nameStyle, color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ValueCell(line: MatchStatLine, column: MatchStatColumn) {
    Box(Modifier.width(column.width()).fillMaxHeight(), contentAlignment = Alignment.Center) {
        val value = line.value(column)
        if (column == MatchStatColumn.RATING) {
            if (value == null) {
                Text("—", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon.copy(alpha = 0.4f))
            } else {
                RatingPill(value.toDouble(), value.toString(), minWidth = 32.dp, height = 24.dp, fontSize = 12.sp, corner = 12.dp)
            }
        } else {
            val n = value ?: 0
            val color = when {
                column == MatchStatColumn.YELLOW && n > 0 -> AmberText
                column == MatchStatColumn.RED && n > 0 -> LossRed
                n == 0 && column != MatchStatColumn.MINUTES -> DeepCarbon.copy(alpha = 0.35f)
                else -> DeepCarbon
            }
            Text(
                if (column == MatchStatColumn.MINUTES) "$n'" else n.toString(),
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

// ---- Events ----

@Composable
private fun EventsTab(details: MatchDetails, modifier: Modifier) {
    // Anyone but the staff only gets the goals: no filters to pick, and the empty case says so.
    val filtering = details.canSeeMatchRecord
    var filter by rememberSaveable { mutableStateOf(EventFilter.ALL) }
    val all = remember(details) { chronologicalEvents(details) }
    val shown = remember(all, filter) { all.filter(filter::accepts) }
    val twoHalves = details.match.halves == 2
    Column(modifier) {
        // The filter stays on top while the events scroll.
        if (filtering) SegmentedFilter(
            options = EventFilter.entries,
            selected = filter,
            label = { stringResource(it.labelRes()) },
            onSelect = { filter = it },
            modifier = Modifier.padding(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = ScreenPadding.Top)
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = if (filtering) 12.dp else ScreenPadding.Top, bottom = ScreenPadding.Bottom),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (shown.isEmpty()) {
                item { Text(stringResource(if (filtering) Res.string.md_no_events else Res.string.md_no_goals), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp)) }
            }
            shown.forEachIndexed { i, event ->
                // A two-half match's events under "1st half" / "2nd half".
                if (twoHalves && (i == 0 || shown[i - 1].period != event.period)) {
                    item(key = "half-${event.period}") {
                        Text(
                            stringResource(if (event.period == 2) Res.string.label_second_half else Res.string.label_first_half).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = DeepCarbon.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = if (i == 0) 4.dp else 10.dp)
                        )
                    }
                }
                item(key = event.key) { RecordEventRow(event, details) }
            }
        }
    }
}

private fun EventFilter.labelRes(): StringResource = when (this) {
    EventFilter.ALL -> Res.string.md_filter_all
    EventFilter.GOALS -> Res.string.md_filter_goals
    EventFilter.SUBSTITUTIONS -> Res.string.md_filter_subs
    EventFilter.CARDS -> Res.string.md_filter_cards
}

/** One event as a white card: its minute, a coloured mark and what happened. [onClick] edits it (the match record editor). */
@Composable
internal fun RecordEventRow(event: DisplayEvent, details: MatchDetails, onClick: (() -> Unit)? = null) {
    val players = remember(details) { details.callUps.associate { it.player.id to it.player } }
    fun nameOf(e: MatchEvent) = e.playerId?.let { players[it] }?.let { "${it.firstName} ${it.lastName}" }
    val opponent = details.opponent.shortName ?: details.opponent.name
    val (mark, markColor, text) = when (event) {
        is DisplayEvent.Substitution -> Triple("⇄", DeepCarbon, stringResource(Res.string.label_event_substitution, nameOf(event.out) ?: "-", nameOf(event.into) ?: "-"))
        is DisplayEvent.Simple -> {
            val e = event.event
            val name = nameOf(e)
            when (e.type) {
                EventType.GOAL -> when {
                    e.isOpponent -> Triple("G", CarbonGray, stringResource(Res.string.label_event_goal, opponent))
                    name != null && event.assist != null -> Triple("G", PitchGreen, stringResource(Res.string.label_event_goal_with_assist, name, nameOf(event.assist) ?: "-"))
                    name != null -> Triple("G", PitchGreen, stringResource(Res.string.label_event_goal, name))
                    else -> Triple("G", PitchGreen, stringResource(Res.string.label_event_goal_unattributed))
                }
                EventType.OWN_GOAL -> Triple("OG", CarbonGray, stringResource(Res.string.label_event_own_goal, name ?: opponent))
                EventType.ASSIST -> Triple("A", PitchGreen, stringResource(Res.string.label_event_assist, name ?: "-"))
                EventType.YELLOW_CARD -> Triple("Y", Amber, stringResource(Res.string.label_event_yellow_card, name ?: "-"))
                EventType.RED_CARD -> Triple("R", LossRed, stringResource(Res.string.label_event_red_card, name ?: "-"))
                EventType.SUBSTITUTION_IN, EventType.SUBSTITUTION_OUT -> Triple("⇄", DeepCarbon, name ?: "-")
            }
        }
    }
    Surface(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(CardShape).clickable(onClick = onClick) else Modifier),
        shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${event.minute}'", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon.copy(alpha = 0.5f), modifier = Modifier.width(38.dp))
            Box(Modifier.size(26.dp).background(markColor.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Text(mark, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (markColor == Amber) AmberText else markColor)
            }
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon, modifier = Modifier.weight(1f))
        }
    }
}

// ---- Timeline ----

/**
 * The pitch at one moment of the game, picked with the slider underneath: kickoff on the left, the last
 * change on the right. On the live console's dark pitch, read-only.
 */
@Composable
private fun TimelineTab(details: MatchDetails, modifier: Modifier) {
    val entries = remember(details) {
        matchTimeline(
            teamSize = details.teamSize,
            startingFormation = details.match.startingFormation,
            callUps = details.callUps.map { it.callUp },
            positions = details.callUps.associate { it.player.id to it.player.position },
            events = details.events,
            changes = details.lineupChanges
        )
    }
    val players = remember(details) { details.callUps.associate { it.player.id to it.player } }
    var index by rememberSaveable(details.match.id) { mutableIntStateOf(0) }
    var ourGoalAtStart by rememberSaveable { mutableStateOf(true) }
    val entry = entries[index.coerceIn(0, entries.lastIndex)]

    BoxWithConstraints(modifier.background(ScreenBackground)) {
        val horizontal = maxWidth > maxHeight
        val pitch: @Composable (Modifier) -> Unit = { pitchModifier ->
            CompositionLocalProvider(LocalPitchOnLight provides true) { PitchPanel(
                formation = entry.formation,
                slots = entry.slots.mapNotNull { (slot, id) -> players[id]?.let { slot to it } }.toMap(),
                bench = entry.bench.mapNotNull { players[it] },
                sentOff = entry.sentOff.mapNotNull { players[it] },
                tokenInfo = { PitchTokenInfo() },
                horizontal = horizontal,
                ourGoalAtStart = ourGoalAtStart,
                dragEnabled = false,
                onSlotTap = null,
                onBenchTap = null,
                onDrop = { _, _ -> },
                formationLabel = entry.formation.formationText(),
                onFormationClick = null,
                onFlip = { ourGoalAtStart = !ourGoalAtStart },
                modifier = pitchModifier
            ) }
        }
        val slider: @Composable () -> Unit = { TimelineSlider(entries, index) { index = it } }
        if (horizontal) {
            Row(Modifier.fillMaxSize().padding(horizontal = ScreenPadding.Horizontal, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pitch(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    TimelineCaption(entry, index, entries.size, players)
                    slider()
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
                TimelineCaption(entry, index, entries.size, players, Modifier.padding(horizontal = ScreenPadding.Horizontal))
                Spacer(Modifier.height(10.dp))
                pitch(Modifier.weight(1f).fillMaxWidth())
                Box(Modifier.padding(horizontal = ScreenPadding.Horizontal).padding(top = 6.dp, bottom = ScreenPadding.Bottom)) { slider() }
            }
        }
    }
}

/** What changed at this moment: "12'  Rafael on for Nuno", and which step of how many it is. */
@Composable
private fun TimelineCaption(entry: TimelineEntry, index: Int, count: Int, players: Map<String, Player>, modifier: Modifier = Modifier) {
    fun name(id: String?) = id?.let { players[it] }?.let { "${it.firstName} ${it.lastName}" } ?: "-"
    val text = when (entry.change) {
        TimelineChange.KICKOFF -> stringResource(Res.string.md_tl_kickoff, entry.formation.formationText())
        TimelineChange.SUBSTITUTION -> stringResource(Res.string.md_tl_sub, name(entry.playerIn), name(entry.playerOut))
        TimelineChange.SENT_OFF -> stringResource(Res.string.md_tl_sent_off, name(entry.playerOut))
        TimelineChange.FORMATION -> stringResource(Res.string.md_tl_formation, entry.formation.formationText())
        TimelineChange.POSITIONS -> stringResource(Res.string.md_tl_positions)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${entry.minute}'", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = DeepCarbon, modifier = Modifier.widthIn(min = 48.dp))
        // Always two lines' room, so the pitch doesn't jump between a short and a long caption: one line
        // sits centred in it, a third is cut with "…".
        val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            // An empty two-line text sets the height, measured like the caption itself (font size and scale included).
            Text("", style = style, minLines = 2, maxLines = 2)
            Text(text, style = style, color = DeepCarbon, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.md_tl_step, index + 1, count), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
    }
}

/** One stop per change, kickoff to the last; the first and last minute at its ends. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineSlider(entries: List<TimelineEntry>, index: Int, onIndex: (Int) -> Unit) {
    // Nothing to slide through when nothing changed after kickoff.
    if (entries.size < 2) return
    val description = stringResource(Res.string.md_tl_slider)
    Column {
        Slider(
            value = index.toFloat(),
            onValueChange = { onIndex(it.roundToInt().coerceIn(0, entries.lastIndex)) },
            valueRange = 0f..entries.lastIndex.toFloat(),
            steps = (entries.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = PitchGreen,
                activeTrackColor = PitchGreen,
                inactiveTrackColor = DeepCarbon.copy(alpha = 0.18f),
                activeTickColor = Color.White.copy(alpha = 0.6f),
                inactiveTickColor = DeepCarbon.copy(alpha = 0.35f)
            ),
            modifier = Modifier.semantics { contentDescription = description }
        )
        Row(Modifier.fillMaxWidth()) {
            Text("${entries.first().minute}'", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            Spacer(Modifier.weight(1f))
            Text("${entries.last().minute}'", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, textAlign = TextAlign.End)
        }
    }
}

// ---- Preview data ----

/** A played 7-a-side match with its record: two goals of ours (one assisted), theirs, a substitution, a booking and a new formation. */
internal fun previewFinishedDetails(canSeeRecord: Boolean): MatchDetails {
    val t0 = kotlin.time.Instant.fromEpochMilliseconds(0)
    fun at(minutes: Int) = t0 + kotlin.time.Duration.parse("${minutes}m")
    val match = com.ricardofrango.squadify.model.Match(
        id = "m1", clubId = "c1", teamId = "t1", opponentId = "o1", location = "Municipal Stadium", dateTime = t0,
        status = com.ricardofrango.squadify.model.MatchStatus.FINISHED, halfLengthMinutes = 25, kickoffAt = t0, secondHalfStartedAt = at(30),
        firstHalfEndMinute = 26, fullTimeMinute = 52, formation = "3-2-1", startingFormation = "2-3-1"
    )
    val base = com.ricardofrango.squadify.presentation.ui.livematch.previewLiveDetails(match)
    fun event(id: String, type: EventType, minute: Int, period: Int, playerId: String? = null, isOpponent: Boolean = false, related: String? = null) =
        MatchEvent(id, "m1", type, minute, playerId = playerId, isOpponent = isOpponent, createdAt = at(minute + if (period == 2) 5 else 0), period = period, relatedEventId = related)
    val events = listOf(
        event("g1", EventType.GOAL, 12, 1, "p7"),
        event("a1", EventType.ASSIST, 12, 1, "p4", related = "g1"),
        event("g2", EventType.GOAL, 20, 1, isOpponent = true),
        event("so", EventType.SUBSTITUTION_OUT, 30, 2, "p6"),
        event("si", EventType.SUBSTITUTION_IN, 30, 2, "p8"),
        event("y1", EventType.YELLOW_CARD, 35, 2, "p2"),
        event("g3", EventType.GOAL, 44, 2, "p8")
    )
    fun line(id: String, started: Boolean, minutes: Int, goals: Int = 0, assists: Int = 0, yellow: Int = 0, rating: Int? = null) =
        com.ricardofrango.squadify.model.MatchPlayerStats("m1", id, started, minutes, goals, assists, 0, yellow, 0, rating)
    val stats = listOf(
        line("p1", true, 52, rating = 7), line("p2", true, 52, yellow = 1, rating = 6), line("p3", true, 52, rating = 7),
        line("p4", true, 52, assists = 1, rating = 8), line("p5", true, 52, rating = 6), line("p6", true, 30, rating = 5),
        line("p7", true, 52, goals = 1, rating = 9), line("p8", false, 22, goals = 1, rating = 8), line("p9", false, 0)
    )
    val kickoff = (0 until 7).associateWith { "p${it + 1}" }
    val changes = listOf(
        com.ricardofrango.squadify.model.MatchLineupChange(0, 1, "2-3-1", kickoff, t0),
        com.ricardofrango.squadify.model.MatchLineupChange(38, 2, "3-2-1", kickoff + (5 to "p8"), at(43))
    )
    return base.copy(
        score = com.ricardofrango.squadify.model.MatchScore("m1", 2, 1),
        canSeeMatchRecord = canSeeRecord,
        events = if (canSeeRecord) events else events.filter { it.type == EventType.GOAL || it.type == EventType.ASSIST },
        playerStats = if (canSeeRecord) stats else emptyList(),
        lineupChanges = if (canSeeRecord) changes else emptyList(),
        report = if (canSeeRecord) com.ricardofrango.squadify.model.MatchReportSummary(com.ricardofrango.squadify.model.MatchReportStatus.DRAFT, ratedPlayers = 8, ratablePlayers = 9, canEdit = true) else null
    )
}
