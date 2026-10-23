package com.ricardofrango.squadify.presentation.ui.livematch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.defaultFormation
import com.ricardofrango.squadify.model.formationText
import com.ricardofrango.squadify.model.parseFormation
import com.ricardofrango.squadify.presentation.ui.components.PersonPhoto
import com.ricardofrango.squadify.presentation.ui.components.PersonPlaceholder
import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// The live pitch view (design "Squadify Pitch View"): our team on a pitch, the goalkeeper in the box
// and the rest in three equal zones, each split evenly by the formation (see Formation.kt). Used by
// the console before kickoff (placing the starters) and during the game (its Pitch tab).

private val FieldColor = Color(0xFF173321)
private val TokenFill = Color(0xFF2C2C2E)
private val PlaceholderGray = Color(0xFF8E8E93)
private val BookedYellow = Color(0xFFFFB300)
private val SentOffRed = Color(0xFFC62828)

/** What a token shows besides the player: how much of the match so far they've played, and a booking. */
internal data class PitchTokenInfo(val minutesFraction: Float? = null, val booked: Boolean = false)

/** Where something can be dragged from or dropped onto. */
internal sealed interface PitchDropKey {
    data class Slot(val slot: Int) : PitchDropKey
    data class Bench(val playerId: String) : PitchDropKey
    /** The bench as a whole, outside any one bench player. */
    data object BenchArea : PitchDropKey
}

/** The player being dragged (held, then moved) and every place they could be dropped, in window coordinates. */
@Stable
internal class PitchDragState {
    var source by mutableStateOf<PitchDropKey?>(null)
        private set
    var player by mutableStateOf<Player?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
    val targets = mutableStateMapOf<PitchDropKey, Rect>()

    fun start(key: PitchDropKey, player: Player, at: Offset) {
        source = key
        this.player = player
        pointer = at
    }

    /** The place under the finger: a token over the bench behind it. */
    fun hovered(): PitchDropKey? {
        val from = source ?: return null
        return targets.entries
            .filter { it.key != from && it.value.contains(pointer) }
            .minByOrNull { if (it.key is PitchDropKey.BenchArea) 1 else 0 }
            ?.key
    }

    fun end(): Pair<PitchDropKey, PitchDropKey?>? {
        val from = source ?: return null
        val to = hovered()
        cancel()
        return from to to
    }

    fun cancel() {
        source = null
        player = null
    }
}

/**
 * A slot's centre as fractions of the pitch. Along the pitch, 0 is our goal line and 1 theirs; across
 * it, 0 is our team's left. Upright, our goal is at the bottom; lying down, on the left, or on the right
 * when [ourGoalAtStart] is false (the whole pitch turned round, so a left-back stays on our left).
 */
private fun pitchPoint(along: Float, across: Float, horizontal: Boolean, ourGoalAtStart: Boolean): Offset = when {
    !horizontal -> Offset(across, 1f - along)
    ourGoalAtStart -> Offset(along, across)
    else -> Offset(1f - along, 1f - across)
}

// Depth of the goalkeeper and of each zone's line, from our goal line: the box, then three equal zones.
private const val KeeperDepth = 0.09f
private val ZoneDepths = listOf(0.32f, 0.59f, 0.86f)

internal fun slotPoint(formation: List<Int>, slot: Int, horizontal: Boolean, ourGoalAtStart: Boolean): Offset {
    val spot = com.ricardofrango.squadify.model.pitchSpot(formation, slot)
    val along = if (spot.zone == 0) KeeperDepth else ZoneDepths[spot.zone - 1]
    val across = (spot.index + 0.5f) / spot.count
    return pitchPoint(along, across, horizontal, ourGoalAtStart)
}

/**
 * Something drawn on the pitch itself (the formation chip), at [x] / [y] as fractions of the pitch as
 * shown (0,0 top-left), kept inside it: (1, 1) is the bottom-right corner.
 */
internal class PitchOverlay(val x: Float, val y: Float, val content: @Composable () -> Unit)

/** The pitch with one [slotContent] per position, each centred on its spot, plus any [overlays]. */
@Composable
internal fun PitchField(
    formation: List<Int>,
    horizontal: Boolean,
    ourGoalAtStart: Boolean,
    modifier: Modifier = Modifier,
    overlays: List<PitchOverlay> = emptyList(),
    slotContent: @Composable (slot: Int) -> Unit
) {
    val slots = formation.sum() + 1
    Layout(
        content = {
            Canvas(Modifier.fillMaxSize()) { drawPitchLines(horizontal, ourGoalAtStart) }
            repeat(slots) { slot -> Box { slotContent(slot) } }
            overlays.forEach { Box { it.content() } }
        },
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FieldColor)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val lines = measurables.first().measure(Constraints.fixed(w, h))
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val tokens = measurables.drop(1).map { it.measure(loose) }
        layout(w, h) {
            lines.place(0, 0)
            tokens.forEachIndexed { index, token ->
                val p = if (index < slots) slotPoint(formation, index, horizontal, ourGoalAtStart)
                else overlays[index - slots].let { Offset(it.x, it.y) }
                val x = (p.x * w - token.width / 2f).roundToInt().coerceIn(0, maxOf(0, w - token.width))
                val y = (p.y * h - token.height / 2f).roundToInt().coerceIn(0, maxOf(0, h - token.height))
                token.place(x, y)
            }
        }
    }
}

private fun DrawScope.drawPitchLines(horizontal: Boolean, ourGoalAtStart: Boolean) {
    val inset = 8.dp.toPx()
    val stroke = Stroke(width = 1.5.dp.toPx())
    val line = Color.White.copy(alpha = 0.22f)
    val field = Rect(inset, inset, size.width - inset, size.height - inset)
    fun at(along: Float, across: Float): Offset {
        val p = pitchPoint(along, across, horizontal, ourGoalAtStart)
        return Offset(field.left + p.x * field.width, field.top + p.y * field.height)
    }
    fun box(alongFrom: Float, alongTo: Float, acrossFrom: Float, acrossTo: Float, color: Color) {
        val a = at(alongFrom, acrossFrom)
        val b = at(alongTo, acrossTo)
        drawRect(color, topLeft = Offset(min(a.x, b.x), min(a.y, b.y)), size = Size(abs(a.x - b.x), abs(a.y - b.y)), style = stroke)
    }
    drawRoundRect(line, topLeft = field.topLeft, size = field.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = stroke)
    drawLine(line, at(0.5f, 0f), at(0.5f, 1f), strokeWidth = stroke.width)
    drawCircle(line, radius = min(field.width, field.height) * 0.12f, center = at(0.5f, 0.5f), style = stroke)
    // Our end reads a little brighter than theirs.
    val ours = Color.White.copy(alpha = 0.30f)
    val theirs = Color.White.copy(alpha = 0.12f)
    box(0f, 0.16f, 0.22f, 0.78f, ours)
    box(0f, 0.06f, 0.38f, 0.62f, ours)
    box(1f, 0.84f, 0.22f, 0.78f, theirs)
    box(1f, 0.94f, 0.38f, 0.62f, theirs)
}

/** A round photo, or the head-and-shoulders placeholder without one. */
@Composable
internal fun TokenPhoto(player: Player, size: Dp, ring: BorderStroke? = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f))) {
    Box(
        Modifier.size(size).clip(CircleShape).background(TokenFill).then(if (ring != null) Modifier.border(ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (player.photoUrl != null) {
            PersonPhoto(player.photoUrl, "${player.firstName.take(1)}${player.lastName.take(1)}", size, background = TokenFill, initialsColor = Color.White)
        } else {
            PersonPlaceholder(size, PlaceholderGray)
        }
    }
}

/** A name to fit under a token: the first name, or with the surname's initial when two players share it. */
internal fun pitchNames(players: List<Player>): Map<String, String> {
    val sharedFirst = players.groupBy { it.firstName.trim().lowercase() }.filterValues { it.size > 1 }.keys
    return players.associate { p ->
        val first = p.firstName.trim()
        p.id to if (first.lowercase() in sharedFirst && p.lastName.isNotBlank()) "$first ${p.lastName.trim().take(1)}." else first
    }
}

/**
 * Registers a token as a drop place and, when [player] is set and dragging is on, lets it be held and
 * dragged. [onDrop] gets where it came from and where it landed (null: nowhere).
 */
@Composable
private fun Modifier.pitchDrag(
    drag: PitchDragState,
    key: PitchDropKey,
    player: Player?,
    enabled: Boolean,
    onDrop: (PitchDropKey, PitchDropKey?) -> Unit
): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestDrop by rememberUpdatedState(onDrop)
    DisposableEffect(key) { onDispose { drag.targets.remove(key) } }
    val positioned = onGloballyPositioned {
        coordinates = it
        drag.targets[key] = it.boundsInWindow()
    }
    if (!enabled || player == null) return positioned
    return positioned.pointerInput(key, player.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset -> coordinates?.let { drag.start(key, player, it.localToWindow(offset)) } },
            onDrag = { change, amount ->
                change.consume()
                drag.pointer += amount
            },
            onDragEnd = { drag.end()?.let { (from, to) -> latestDrop(from, to) } },
            onDragCancel = { drag.cancel() }
        )
    }
}

@Composable
private fun PitchPlayerToken(
    player: Player,
    name: String,
    info: PitchTokenInfo,
    photo: Dp,
    highlighted: Boolean,
    dimmed: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier
) {
    val compact = photo < 40.dp
    val ring = when {
        highlighted -> BorderStroke(2.dp, PitchGreen)
        else -> BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f))
    }
    Column(
        modifier
            .width(photo + 20.dp)
            .alpha(if (dimmed) 0.35f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = listOfNotNull(player.shirtNumber?.toString(), "${player.firstName} ${player.lastName}").joinToString(" ") },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        info.minutesFraction?.let { fraction ->
            Box(Modifier.width(photo).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(PitchGreen))
            }
            Spacer(Modifier.height(4.dp))
        }
        Box {
            TokenPhoto(player, photo, ring)
            player.shirtNumber?.let { number ->
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp)
                        .widthIn(min = 18.dp).height(18.dp)
                        .background(DeepCarbon, RoundedCornerShape(9.dp))
                        .border(1.5.dp, PitchGreen, RoundedCornerShape(9.dp))
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(number.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            if (info.booked) {
                Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp).size(width = 7.dp, height = 10.dp).background(BookedYellow, RoundedCornerShape(1.dp)))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.background(DeepCarbon.copy(alpha = 0.55f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)
        )
    }
}

/** An empty position: a dashed ring with "+", and "Empty" under it. */
@Composable
private fun EmptySlotToken(photo: Dp, highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier) {
    val compact = photo < 40.dp
    val color = if (highlighted) PitchGreen else Color.White.copy(alpha = 0.45f)
    val label = stringResource(Res.string.lm_empty_position)
    Column(
        modifier
            .width(photo + 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(photo)) {
            drawCircle(
                color,
                radius = size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
            )
            val arm = size.minDimension * 0.18f
            drawLine(color, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), strokeWidth = 2.dp.toPx())
            drawLine(color, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), strokeWidth = 2.dp.toPx())
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = if (compact) 10.sp else 11.sp, maxLines = 1)
    }
}

@Composable
private fun BenchToken(player: Player, name: String, info: PitchTokenInfo, highlighted: Boolean, dimmed: Boolean, onClick: (() -> Unit)?, modifier: Modifier) {
    Column(
        modifier
            .width(56.dp)
            .alpha(if (dimmed) 0.35f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = listOfNotNull(player.shirtNumber?.toString(), "${player.firstName} ${player.lastName}").joinToString(" ") }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        info.minutesFraction?.let { fraction ->
            Box(Modifier.width(34.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(PitchGreen))
            }
            Spacer(Modifier.height(4.dp))
        }
        Box {
            TokenPhoto(player, 34.dp, if (highlighted) BorderStroke(2.dp, PitchGreen) else null)
            player.shirtNumber?.let {
                Text(
                    it.toString(),
                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp).background(DeepCarbon, RoundedCornerShape(6.dp)).padding(horizontal = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(name, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The pitch, the bench and anyone sent off: the bench is a row under the pitch either way. Lying down
 * ([horizontal]) the pitch is drawn across, with the flip button on it, and [footer] (the kick-off
 * button, before kickoff) is a column to the right.
 */
@Composable
internal fun PitchPanel(
    formation: List<Int>,
    slots: Map<Int, Player>,
    bench: List<Player>,
    sentOff: List<Player>,
    tokenInfo: (Player) -> PitchTokenInfo,
    horizontal: Boolean,
    ourGoalAtStart: Boolean,
    dragEnabled: Boolean,
    onSlotTap: ((slot: Int, player: Player?) -> Unit)?,
    onBenchTap: ((Player) -> Unit)?,
    onDrop: (from: PitchDropKey, to: PitchDropKey?) -> Unit,
    // The formation chip, on the pitch beside the goalkeeper (on our right); tapping it changes the formation.
    formationLabel: String,
    onFormationClick: (() -> Unit)?,
    // Lying down: turns the pitch round (a button on the pitch's top corner at the far end).
    onFlip: (() -> Unit)?,
    hint: String?,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    // Before kickoff: the half-length chip, on the pitch on the goalkeeper's other side.
    besideKeeper: (@Composable () -> Unit)? = null
) {
    val drag = remember { PitchDragState() }
    val names = remember(slots, bench, sentOff) { pitchNames(slots.values + bench + sentOff) }
    val hovered = drag.hovered()
    var host by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val pitch: @Composable (Modifier) -> Unit = { pitchModifier ->
      BoxWithConstraints(pitchModifier) {
        // Each token gets the room its zone leaves it: the most crowded line (four defenders across a
        // pitch lying down) shrinks every photo, so neighbours never overlap.
        val crowd = maxOf(1, formation.maxOrNull() ?: 1)
        val cell = (if (horizontal) maxHeight else maxWidth) / crowd
        val photo = (cell - if (horizontal) 36.dp else 26.dp).coerceIn(26.dp, 44.dp)
        // The chips sit in the pitch's corners at our goal's end, the formation chip always at the
        // bottom: upright, the two bottom corners; lying down, the bottom and top corners of our end.
        val ourEndX = if (!horizontal || ourGoalAtStart) 0f else 1f
        val formationAt = if (horizontal) Offset(ourEndX, 1f) else Offset(1f, 1f)
        val halfLengthAt = if (horizontal) Offset(ourEndX, 0f) else Offset(0f, 1f)
        val overlays = listOfNotNull(
            PitchOverlay(formationAt.x, formationAt.y) { Box(Modifier.padding(6.dp)) { FormationChip(formationLabel, onFormationClick) } },
            besideKeeper?.let { content -> PitchOverlay(halfLengthAt.x, halfLengthAt.y) { Box(Modifier.padding(6.dp)) { content() } } },
            onFlip?.takeIf { horizontal }?.let { flip -> PitchOverlay(1f - ourEndX, 0f) { Box(Modifier.padding(6.dp)) { FlipPitchButton(flip) } } }
        )
        PitchField(formation, horizontal, ourGoalAtStart, Modifier.fillMaxSize(), overlays) { slot ->
            val key = PitchDropKey.Slot(slot)
            val player = slots[slot]
            val tap = onSlotTap?.let { { it(slot, player) } }
            if (player != null) {
                PitchPlayerToken(
                    player, names[player.id] ?: player.firstName, tokenInfo(player),
                    photo = photo,
                    highlighted = hovered == key,
                    dimmed = drag.source == key,
                    onClick = tap,
                    modifier = Modifier.pitchDrag(drag, key, player, dragEnabled, onDrop)
                )
            } else {
                EmptySlotToken(photo, hovered == key, tap, Modifier.pitchDrag(drag, key, null, false, onDrop))
            }
        }
      }
    }
    val benchToken: @Composable (Player) -> Unit = { player ->
        val key = PitchDropKey.Bench(player.id)
        BenchToken(
            player, names[player.id] ?: player.firstName, tokenInfo(player),
            highlighted = hovered == key,
            dimmed = drag.source == key,
            onClick = onBenchTap?.let { { it(player) } },
            modifier = Modifier.pitchDrag(drag, key, player, dragEnabled, onDrop)
        )
    }
    val sentOffLine: @Composable () -> Unit = {
        if (sentOff.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(0.6f)) {
                Box(Modifier.size(width = 8.dp, height = 12.dp).background(SentOffRed, RoundedCornerShape(1.dp)))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.lm_sent_off_list, sentOff.joinToString(", ") { p -> listOfNotNull(p.shirtNumber?.let { "#$it" }, names[p.id]).joinToString(" ") }),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
    val hintLine: @Composable () -> Unit = {
        hint?.let { Text(it, color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall) }
    }
    val benchArea = Modifier.pitchDrag(drag, PitchDropKey.BenchArea, null, false, onDrop)

    Box(modifier.onGloballyPositioned { host = it }) {
        // The pitch, with the bench in a row under it (and anyone sent off). Upright the screen's side
        // padding applies here; lying down the caller pads. Before kickoff, lying down, [footer] (the
        // kick-off button) is a column to the right.
        val side = if (horizontal) 0.dp else ScreenPadding.Horizontal
        val stack: @Composable (Modifier) -> Unit = { stackModifier ->
            Column(stackModifier) {
                pitch(Modifier.weight(1f).fillMaxWidth().padding(horizontal = side))
                Column(Modifier.fillMaxWidth().padding(horizontal = side).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hintLine()
                    Row(
                        benchArea.fillMaxWidth().height(64.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) { bench.forEach { benchToken(it) } }
                    sentOffLine()
                }
            }
        }
        if (horizontal && footer != null) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                stack(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) { footer() }
            }
        } else {
            stack(Modifier.fillMaxSize())
        }

        // The player being dragged follows the finger.
        val dragged = drag.player
        val hostCoordinates = host
        if (dragged != null && hostCoordinates != null) {
            val local = hostCoordinates.windowToLocal(drag.pointer)
            Box(Modifier.offset { IntOffset((local.x - 28.dp.toPx()).roundToInt(), (local.y - 28.dp.toPx()).roundToInt()) }) {
                TokenPhoto(dragged, 56.dp, BorderStroke(2.dp, PitchGreen))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Formation bar and sheets
// ---------------------------------------------------------------------------------------------

/** The formation ("2-3-1") as a chip on the pitch; tapping it opens the formation sheet. */
@Composable
internal fun FormationChip(formation: String, onClick: (() -> Unit)?) {
    val description = stringResource(Res.string.lm_change_formation)
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(14.dp),
        color = DeepCarbon.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = if (onClick != null) Modifier.semantics { contentDescription = "$description: $formation" } else Modifier
    ) {
        Text(formation, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Before kickoff: the half length as a chip ("30 min") that opens its options. */
@Composable
internal fun HalfLengthChip(minutes: Int, options: List<Int>, enabled: Boolean, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(Res.string.lm_half_length_cd, minutes)
    Box {
        Surface(
            onClick = { open = true },
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            color = CarbonGray,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.semantics { contentDescription = description }
        ) {
            Text(stringResource(Res.string.lm_minutes_chip, minutes) + " ▾", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.lm_minutes_chip, option), fontWeight = if (option == minutes) FontWeight.Bold else null) },
                    onClick = { open = false; onPick(option) }
                )
            }
        }
    }
}

/** Landscape only: turns the pitch round on this device. The scoreboard never moves. */
@Composable
internal fun FlipPitchButton(onClick: () -> Unit) {
    val description = stringResource(Res.string.lm_flip_pitch)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.size(width = 40.dp, height = 32.dp).semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center) { Text("⇄", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

/** The common shapes for a team size, its default first, then the closest to it. */
internal fun quickFormations(teamSize: Int, count: Int = 5): List<List<Int>> {
    val outfield = teamSize - 1
    val default = defaultFormation(teamSize)
    if (outfield < 3) return listOf(default)
    val all = buildList {
        for (d in 1..outfield) for (m in 1..outfield) {
            val a = outfield - d - m
            if (a >= 1) add(listOf(d, m, a))
        }
    }
    return all.sortedWith(compareBy<List<Int>> { f -> f.indices.sumOf { abs(f[it] - default[it]) } }.thenByDescending { it[0] }).take(count)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FormationSheet(current: String, teamSize: Int, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    val parsed = parseFormation(text, teamSize)
    val outfield = teamSize - 1
    val numbers = text.trim().split("-").map { it.trim().toIntOrNull() }
    val error = when {
        parsed != null || text.isBlank() -> null
        numbers.size == 3 && numbers.all { it != null } ->
            stringResource(Res.string.lm_formation_wrong_sum, text.trim(), numbers.sumOf { it ?: 0 }, outfield)
        else -> stringResource(Res.string.lm_formation_wrong_format, defaultFormation(teamSize).formationText())
    }
    val apply = { parsed?.let { onApply(it.formationText()) } }
    val muted = Color.White.copy(alpha = 0.55f)
    // Design "Squadify Pitch View": title with the team size, a filled field (red edge when wrong),
    // the sum, the error, quick picks, and flat Cancel / Apply.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CarbonGray) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.lm_change_formation), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(stringResource(Res.string.lm_team_size_side, teamSize), color = muted, fontSize = 12.sp)
            }
            Text(stringResource(Res.string.lm_formation_format).uppercase(), color = muted, fontSize = 12.sp, letterSpacing = 0.5.sp)
            OutlinedTextField(
                value = text,
                onValueChange = { new -> if (new.length <= 8 && new.all { it.isDigit() || it == '-' || it == ' ' }) text = new },
                singleLine = true,
                isError = error != null,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                keyboardActions = submitKeyboardActions { apply() },
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, color = Color.White),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2C2C2E),
                    unfocusedContainerColor = Color(0xFF2C2C2E),
                    errorContainerColor = Color(0xFF2C2C2E),
                    focusedBorderColor = PitchGreen,
                    unfocusedBorderColor = Color.Transparent,
                    errorBorderColor = Color(0xFFC62828),
                    cursorColor = PitchGreen,
                    errorCursorColor = PitchGreen
                )
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.lm_formation_adds_up, outfield), color = muted, fontSize = 12.sp)
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp) }
            }
            Text(stringResource(Res.string.lm_quick_picks).uppercase(), color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickFormations(teamSize).forEach { f ->
                    val label = f.formationText()
                    val selected = parsed?.formationText() == label
                    Surface(onClick = { text = label }, shape = RoundedCornerShape(16.dp), color = if (selected) PitchGreen else Color.White.copy(alpha = 0.06f)) {
                        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = onDismiss, shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.06f), modifier = Modifier.weight(1f).height(46.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(stringResource(Res.string.btn_cancel), color = muted, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
                Surface(onClick = { apply() }, enabled = parsed != null, shape = RoundedCornerShape(10.dp), color = PitchGreen.copy(alpha = if (parsed != null) 1f else 0.35f), modifier = Modifier.weight(1f).height(46.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.btn_apply), color = Color.White.copy(alpha = if (parsed != null) 1f else 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** One row of a pick sheet: a player (with their photo), or a plain line such as an empty position. */
internal data class PitchPickRow(val label: String, val player: Player? = null, val onClick: () -> Unit)
internal data class PitchPickSection(val title: String?, val rows: List<PitchPickRow>)

/** A sheet of choices: who plays in a position, who to put in instead, who to swap with. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PitchPickSheet(title: String, subtitle: String?, sections: List<PitchPickSection>, emptyText: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CarbonGray) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding.Horizontal).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            subtitle?.let { Text(it, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            if (sections.all { it.rows.isEmpty() } && emptyText != null) {
                Text(emptyText, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
            }
            sections.filter { it.rows.isNotEmpty() }.forEach { section ->
                section.title?.let {
                    Text(it.uppercase(), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                section.rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = row.onClick).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.player?.let {
                            TokenPhoto(it, 36.dp, null)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(row.label, color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** "10 · Tomás Bragança", or the name alone without a shirt number. */
internal fun pickLabel(player: Player): String =
    listOfNotNull(player.shirtNumber?.toString(), "${player.firstName} ${player.lastName}").joinToString(" · ")

@Composable
internal fun zoneLabel(zone: Int): String = stringResource(
    when (zone) {
        0 -> Res.string.lm_zone_goal
        1 -> Res.string.lm_zone_defence
        2 -> Res.string.lm_zone_midfield
        else -> Res.string.lm_zone_attack
    }
)
