package com.ricardofrango.squadify.presentation.ui.livematch

import androidx.compose.ui.text.input.ImeAction
import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import com.ricardofrango.squadify.presentation.ui.components.PlayerBadge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.EventType
import com.ricardofrango.squadify.model.MatchDetails
import com.ricardofrango.squadify.model.MatchEvent
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerMinutes
import com.ricardofrango.squadify.model.FAIR_SHARE_FROM_MINUTE
import com.ricardofrango.squadify.model.elapsedMatchMinutes
import com.ricardofrango.squadify.model.fairShareMinutes
import com.ricardofrango.squadify.model.isBelowShare
import com.ricardofrango.squadify.model.matchHalves
import com.ricardofrango.squadify.model.playerMinutes
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*
import kotlin.math.roundToInt

// Live minutes played (design 36): every Squad row ends in the player's minutes with a thin bar
// against the fair share, the clock gets a real pause button and opens its own correction sheet, the
// substitution pickers list candidates with their minutes, and full time shows a minutes table.

internal val MinutesAmber = Color(0xFFF1C40F)
private val StintWarnMinutes = 15

/** Minutes for the whole squad at one moment, plus the share they're judged against. */
internal data class SquadMinutes(
    val byPlayer: Map<String, PlayerMinutes>,
    val elapsed: Int,
    val share: Double,
    val calledUp: Int,
    val playersOnPitch: Int
) {
    /** Shares are meaningless in the first minutes: no bars or flags before then. */
    val early: Boolean get() = elapsed < FAIR_SHARE_FROM_MINUTE

    /**
     * With nobody on the bench everyone plays every minute -- always exactly the share -- so the bars
     * (all stuck at ~60%), the share line and the flags would only suggest a problem that can't exist.
     */
    val hasBench: Boolean get() = calledUp > playersOnPitch

    /** Whether the share, the bars and the "below share" flags are shown at all. */
    val showShare: Boolean get() = hasBench && !early
    fun of(id: String): PlayerMinutes? = byPlayer[id]
    fun minutes(id: String): Int = byPlayer[id]?.minutes ?: 0
    fun below(id: String): Boolean = hasBench && isBelowShare(minutes(id), share, elapsed)
    fun notPlayed(id: String): Boolean = byPlayer[id]?.hasPlayed == false

    /**
     * The bar: how much of the match so far the player has played. At 20' a player with 20' is full,
     * one with 10' is half, and one who hasn't played is all grey.
     */
    fun barFraction(id: String): Float =
        if (elapsed <= 0) 0f else (minutes(id).toFloat() / elapsed).coerceIn(0f, 1f)
}

/** [liveMinute]: the running clock's minute while a half is played; null at half time / full time. */
internal fun squadMinutes(details: MatchDetails, events: List<MatchEvent>, liveMinute: Int?): SquadMinutes {
    val callUps = details.callUps.map { it.callUp }
    val elapsed = elapsedMatchMinutes(matchHalves(details.match, liveMinute))
    val onPitch = callUps.count { it.isStarting }.takeIf { it > 0 } ?: details.teamSize
    return SquadMinutes(
        byPlayer = playerMinutes(details.match, callUps, events, liveMinute),
        elapsed = elapsed,
        share = fairShareMinutes(elapsed, onPitch, callUps.size),
        calledUp = callUps.size,
        playersOnPitch = onPitch
    )
}

/** A minute count as shown everywhere: "18'". */
internal fun mins(n: Int) = "$n'"

private fun firstName(player: Player) = player.firstName

// ---------------------------------------------------------------------------------------------
// Clock block: tap-to-correct clock + pause button (36c)
// ---------------------------------------------------------------------------------------------

/** "Correct the clock" (36c): the current minute with −1 / +1 and direct entry; never confirms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClockCorrectionSheet(initialMinute: Int, isPaused: Boolean, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialMinute.toString()) }
    val value = text.toIntOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CarbonGray) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(stringResource(Res.string.lm_correct_clock), color = Color.White, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(if (isPaused) Res.string.lm_correct_paused else Res.string.lm_correct_running),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                StepButton("−1") { text = ((value ?: initialMinute) - 1).coerceAtLeast(0).toString() }
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> if (new.length <= 3 && new.all { it.isDigit() }) text = new },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    // The keyboard key saves the corrected minute, like the Save button.
                    keyboardActions = submitKeyboardActions { value?.let(onSave) },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.End),
                    modifier = Modifier.width(96.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PitchGreen, unfocusedBorderColor = Color.White.copy(alpha = 0.3f), cursorColor = PitchGreen
                    )
                )
                Text(":00", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
                Spacer(Modifier.width(16.dp))
                StepButton("+1") { text = ((value ?: initialMinute) + 1).toString() }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = onDismiss, shape = RoundedCornerShape(14.dp), color = Color.Transparent, border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), modifier = Modifier.weight(1f).height(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(stringResource(Res.string.btn_cancel), color = Color.White, fontWeight = FontWeight.Bold) }
                }
                Surface(onClick = { value?.let(onSave) }, enabled = value != null, shape = RoundedCornerShape(14.dp), color = if (value != null) PitchGreen else PitchGreen.copy(alpha = 0.4f), modifier = Modifier.weight(1f).height(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(stringResource(Res.string.btn_save), color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = 0.08f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) }
    }
}

// ---------------------------------------------------------------------------------------------
// Squad tab with minutes (36a, 36d, 36f)
// ---------------------------------------------------------------------------------------------

internal data class SquadPlayerRow(val player: Player, val goals: Int, val hasYellow: Boolean)
internal data class DerivedSquad(
    val onPitch: List<SquadPlayerRow>,
    val bench: List<SquadPlayerRow>,
    val sentOff: List<SquadPlayerRow>
)

/** On pitch / bench / sent off, from the same minutes walk as the counts (so the two always agree). */
internal fun deriveSquad(details: MatchDetails, events: List<MatchEvent>, minutes: SquadMinutes): DerivedSquad {
    val own = events.filter { !it.isOpponent }
    fun row(player: Player) = SquadPlayerRow(
        player,
        goals = own.count { it.playerId == player.id && it.type == EventType.GOAL },
        hasYellow = own.any { it.playerId == player.id && it.type == EventType.YELLOW_CARD }
    )
    val players = details.callUps.map { it.player }
    val onPitch = players.filter { minutes.of(it.id)?.onPitch == true }
        .sortedWith(compareByDescending<Player> { minutes.minutes(it.id) }.thenBy { it.shirtNumber ?: Int.MAX_VALUE })
    val sentOff = players.filter { minutes.of(it.id)?.sentOff == true }
    val bench = players.filter { it !in onPitch && it !in sentOff }
    return DerivedSquad(onPitch.map(::row), bench.map(::row), sentOff.map(::row))
}

internal enum class BenchSort { FEWEST, SHIRT }

internal fun List<SquadPlayerRow>.sortedForBench(sort: BenchSort, minutes: SquadMinutes) = when (sort) {
    BenchSort.FEWEST -> sortedWith(compareBy<SquadPlayerRow> { minutes.minutes(it.player.id) }.thenBy { it.player.shirtNumber ?: Int.MAX_VALUE })
    BenchSort.SHIRT -> sortedBy { it.player.shirtNumber ?: Int.MAX_VALUE }
}

/** Header lines above the squad: share (or "shares from 5'"), paused note, and a summary strip. */
@Composable
private fun ShareHeader(minutes: SquadMinutes, squad: DerivedSquad, isPaused: Boolean) {
    val players = squad.onPitch + squad.bench + squad.sentOff
    if (!minutes.hasBench) {
        if (isPaused) Text(stringResource(Res.string.lm_minutes_paused), color = MinutesAmber, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
        return
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        val share = if (minutes.early) {
            stringResource(Res.string.lm_share_from, mins(FAIR_SHARE_FROM_MINUTE))
        } else {
            stringResource(Res.string.lm_fair_share, mins(minutes.share.roundToInt()))
        }
        Text(
            if (isPaused) "$share · ${stringResource(Res.string.lm_minutes_paused)}" else share,
            color = if (isPaused) MinutesAmber else Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
        )
        if (!minutes.early) {
            val notPlayed = players.count { minutes.notPlayed(it.player.id) }
            val below = players.count { !minutes.notPlayed(it.player.id) && minutes.below(it.player.id) }
            // Answers "who needs minutes?" without scrolling, which matters most on an 18-player list.
            if (notPlayed + below > 0) {
                Text(
                    listOfNotNull(
                        notPlayed.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.lm_summary_not_played, it, it) },
                        below.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.lm_summary_below, it, it) }
                    ).joinToString(" · "),
                    color = MinutesAmber,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

/** Half time (36d): first-half totals and the three furthest below the share, to plan the 2nd half. */
@Composable
private fun HalfTimePlanCard(minutes: SquadMinutes, squad: DerivedSquad, firstHalfLabel: String) {
    val furthest = (squad.onPitch + squad.bench)
        .filter { minutes.below(it.player.id) }
        .sortedBy { minutes.minutes(it.player.id) }
        .take(3)
    Surface(shape = RoundedCornerShape(14.dp), color = MinutesAmber.copy(alpha = 0.1f), border = BorderStroke(1.dp, MinutesAmber.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(Res.string.lm_plan_title), color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            Text(
                if (furthest.isEmpty()) stringResource(Res.string.lm_plan_none)
                else stringResource(Res.string.lm_plan_below, furthest.joinToString(" · ") { "${firstName(it.player)} ${mins(minutes.minutes(it.player.id))}" }),
                color = MinutesAmber,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.lm_first_half_totals, firstHalfLabel), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FlashLine(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = PitchGreen.copy(alpha = 0.18f), border = BorderStroke(1.dp, PitchGreen.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun SectionHeading(text: String, color: Color = Color.White.copy(alpha = 0.5f), trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = color, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp), modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * The Squad tab: share header, half-time plan, the last substitution / clock correction, then On
 * pitch (most minutes first), Bench (fewest minutes first by default) and Sent off. [sideBySide]
 * (landscape, 36f) puts On pitch and Bench in two columns.
 */
@Composable
internal fun SquadMinutesTab(
    squad: DerivedSquad,
    minutes: SquadMinutes,
    isPaused: Boolean,
    isHalfTime: Boolean,
    firstHalfLabel: String,
    flash: String?,
    canManage: Boolean,
    sideBySide: Boolean,
    onPlayerClick: (SquadPlayerRow, onPitch: Boolean) -> Unit
) {
    var benchSort by rememberSaveable { mutableStateOf(BenchSort.FEWEST) }
    // A long list (11-a-side, 18 called up) tightens the rows so more fit on screen.
    val compact = minutes.calledUp > 14
    val bench = squad.bench.sortedForBench(benchSort, minutes)

    fun LazyListScope.header() {
        item { ShareHeader(minutes, squad, isPaused) }
        // Planning who needs minutes only makes sense with someone on the bench.
        if (isHalfTime && minutes.hasBench) item { HalfTimePlanCard(minutes, squad, firstHalfLabel) }
        flash?.let { item { FlashLine(it) } }
    }
    fun LazyListScope.onPitch() {
        item { SectionHeading(stringResource(Res.string.label_on_pitch_count, squad.onPitch.size)) }
        items(squad.onPitch, key = { "p-" + it.player.id }) { row ->
            MinutesRow(row, RowKind.ON_PITCH, minutes, isPaused, compact, onClick = if (canManage) { { onPlayerClick(row, true) } } else null)
        }
    }
    fun LazyListScope.benchAndSentOff() {
        if (bench.isNotEmpty()) {
            item {
                SectionHeading("${stringResource(Res.string.label_bench)} ${bench.size}") {
                    Text(
                        stringResource(if (benchSort == BenchSort.FEWEST) Res.string.lm_sort_fewest else Res.string.lm_sort_shirt),
                        color = PitchGreen,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { benchSort = if (benchSort == BenchSort.FEWEST) BenchSort.SHIRT else BenchSort.FEWEST }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            items(bench, key = { "b-" + it.player.id }) { row ->
                MinutesRow(row, RowKind.BENCH, minutes, isPaused, compact, onClick = if (canManage) { { onPlayerClick(row, false) } } else null)
            }
        }
        if (squad.sentOff.isNotEmpty()) {
            item { SectionHeading(stringResource(Res.string.label_sent_off), color = Color(0xFFC0392B)) }
            items(squad.sentOff, key = { "s-" + it.player.id }) { row ->
                MinutesRow(row, RowKind.SENT_OFF, minutes, isPaused, compact, onClick = null)
            }
        }
    }

    val padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    val spacing = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    if (sideBySide) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = padding, verticalArrangement = spacing) { header(); onPitch() }
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = padding, verticalArrangement = spacing) { benchAndSentOff() }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = spacing) {
            header(); onPitch(); benchAndSentOff()
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

internal enum class RowKind { ON_PITCH, BENCH, SENT_OFF }

/** The spell line: "on for 18'" (amber past 15'), "off since 9'", or "Not played yet" in amber. */
@Composable
internal fun stintLine(id: String, kind: RowKind, minutes: SquadMinutes): Pair<String, Boolean> {
    val pm = minutes.of(id)
    val offAt = pm?.lastOffMinute
    return when {
        pm == null || !pm.hasPlayed -> stringResource(Res.string.lm_not_played) to true
        kind == RowKind.ON_PITCH -> stringResource(Res.string.lm_on_for, mins(pm.stintMinutes)) to (pm.stintMinutes > StintWarnMinutes)
        offAt != null -> stringResource(Res.string.lm_off_since, mins(offAt)) to false
        else -> "" to false
    }
}

@Composable
private fun MinutesRow(row: SquadPlayerRow, kind: RowKind, minutes: SquadMinutes, isPaused: Boolean, compact: Boolean, onClick: (() -> Unit)?) {
    val id = row.player.id
    val bg = when (kind) {
        RowKind.ON_PITCH -> PitchGreen.copy(alpha = 0.16f)
        RowKind.BENCH -> CarbonGray
        RowKind.SENT_OFF -> Color(0xFFC0392B).copy(alpha = 0.12f)
    }
    val border = when (kind) {
        RowKind.ON_PITCH -> BorderStroke(1.dp, PitchGreen.copy(alpha = 0.4f))
        RowKind.SENT_OFF -> BorderStroke(1.dp, Color(0xFFC0392B).copy(alpha = 0.4f))
        RowKind.BENCH -> null
    }
    val notPlayed = minutes.notPlayed(id)
    val below = minutes.showShare && !notPlayed && minutes.below(id)
    val (line, lineWarn) = stintLine(id, kind, minutes)
    val countColor = when {
        kind == RowKind.SENT_OFF -> Color.White.copy(alpha = 0.5f)
        isPaused && kind == RowKind.ON_PITCH -> MinutesAmber
        notPlayed -> MinutesAmber
        else -> Color.White
    }
    Surface(onClick = onClick ?: {}, enabled = onClick != null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = bg, border = border) {
        Column(Modifier.padding(start = 12.dp, end = 14.dp, top = if (compact) 7.dp else 10.dp, bottom = if (compact) 6.dp else 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerBadge(
                    row.player.photoUrl, row.player.shirtNumber,
                    photoSize = if (compact) 24.dp else 28.dp,
                    numberColor = if (kind == RowKind.SENT_OFF) Color(0xFFC0392B) else PitchGreen,
                    placeholderColor = Color.White.copy(alpha = 0.4f),
                    numberStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black)
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${row.player.firstName} ${row.player.lastName}",
                            color = if (kind == RowKind.SENT_OFF) Color.White.copy(alpha = 0.5f) else Color.White,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (row.goals > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text("⚽${if (row.goals > 1) row.goals else ""}", color = PitchGreen, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        if (row.hasYellow) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(width = 9.dp, height = 12.dp).background(Color(0xFFF1C40F), RoundedCornerShape(1.5.dp)))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (line.isNotEmpty()) {
                            Text(
                                line,
                                color = if (lineWarn) MinutesAmber else Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (lineWarn) FontWeight.Bold else FontWeight.Normal)
                            )
                        }
                        if (below) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(Res.string.lm_below_share),
                                color = MinutesAmber,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.background(MinutesAmber.copy(alpha = 0.15f), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    mins(minutes.minutes(id)),
                    color = countColor,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 52.dp)
                )
            }
            if (minutes.elapsed > 0) {
                Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
                MinutesBar(minutes.barFraction(id))
            }
        }
    }
}

/** Green for the part of the match played, on a grey track for the rest. */
@Composable
internal fun MinutesBar(fraction: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(2.dp))) {
        Box(Modifier.width(maxWidth * fraction).fillMaxHeight().background(PitchGreen, RoundedCornerShape(2.dp)))
    }
}

// ---------------------------------------------------------------------------------------------
// Substitution pickers with minutes (36b)
// ---------------------------------------------------------------------------------------------

/**
 * Step 2 of a substitution: the chosen player's minutes, then the candidates -- the bench fewest
 * minutes first when taking someone off, the pitch longest-on first when bringing someone on --
 * so the fair choice is on top. Picking a candidate swaps immediately.
 */
@Composable
internal fun SubstitutionPicker(
    chosen: Player,
    takingOff: Boolean,
    candidates: List<SquadPlayerRow>,
    minutes: SquadMinutes,
    enabled: Boolean,
    onPick: (Player) -> Unit
) {
    val name = "${chosen.firstName} ${chosen.lastName}"
    val pm = minutes.of(chosen.id)
    val (line, _) = stintLine(chosen.id, if (takingOff) RowKind.ON_PITCH else RowKind.BENCH, minutes)
    SheetPlayerHeader(
        chosen,
        stringResource(if (takingOff) Res.string.lm_take_off else Res.string.lm_bring_on, name),
        listOf(stringResource(Res.string.lm_played, mins(pm?.minutes ?: 0)), line).filter { it.isNotEmpty() }.joinToString(" · ")
    )
    Spacer(Modifier.height(14.dp))
    Text(
        stringResource(if (takingOff) Res.string.lm_who_comes_on else Res.string.lm_who_goes_off).uppercase(),
        color = Color.White.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    )
    Spacer(Modifier.height(8.dp))
    val sorted = if (takingOff) {
        candidates.sortedForBench(BenchSort.FEWEST, minutes)
    } else {
        candidates.sortedWith(compareByDescending<SquadPlayerRow> { minutes.of(it.player.id)?.stintMinutes ?: 0 }.thenByDescending { minutes.minutes(it.player.id) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sorted.forEach { row ->
            MinutesRow(row, if (takingOff) RowKind.BENCH else RowKind.ON_PITCH, minutes, isPaused = false, compact = true, onClick = if (enabled) { { onPick(row.player) } } else null)
        }
    }
}
