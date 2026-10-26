package com.ricardofrango.squadify.presentation.ui.training

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.cd_move_down
import squadify.app.shared.generated.resources.cd_move_up
import squadify.app.shared.generated.resources.cd_reorder
import squadify.app.shared.generated.resources.ic_drag_handle

// Reordering a short list by dragging each row's handle (a session's exercises): the row follows the
// finger, the rows it passes make room, and the new order is handed back on release. Screen readers
// get "Move up" / "Move down" on the handle instead.

/**
 * Where the row at [from] lands when dragged [offset] pixels (down is positive), given each row's
 * height: past the middle of a neighbour, it takes that neighbour's place.
 */
internal fun dragTarget(from: Int, offset: Float, heights: List<Int>): Int {
    var target = from
    var passed = 0f
    if (offset > 0) {
        while (target + 1 < heights.size && offset > passed + heights[target + 1] / 2f) {
            passed += heights[target + 1]
            target++
        }
    } else {
        while (target - 1 >= 0 && -offset > passed + heights[target - 1] / 2f) {
            passed += heights[target - 1]
            target--
        }
    }
    return target
}

/**
 * [items] one under the other, each drawn by [row] with the modifier its drag handle takes
 * ([DragHandle]). While [enabled], dragging a handle moves its row; dropping it elsewhere calls
 * [onMove] with the old and new index.
 */
@Composable
fun <T> DragReorderColumn(
    items: List<T>,
    enabled: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    row: @Composable (index: Int, item: T, handle: Modifier) -> Unit
) {
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val move by rememberUpdatedState(onMove)
    val heightList = List(items.size) { heights[it] ?: 0 }
    val target = dragging?.let { dragTarget(it, offset, heightList) }
    val moveUpLabel = stringResource(Res.string.cd_move_up)
    val moveDownLabel = stringResource(Res.string.cd_move_down)

    Column {
        items.forEachIndexed { i, item ->
            val from = dragging
            // The dragged row follows the finger; those it passed shift by its height to make room.
            val shift = when {
                from == null || target == null -> 0f
                i == from -> offset
                i in (from + 1)..target -> -(heights[from] ?: 0).toFloat()
                i in target until from -> (heights[from] ?: 0).toFloat()
                else -> 0f
            }
            val handle = Modifier
                .semantics {
                    if (enabled) {
                        customActions = listOfNotNull(
                            if (i > 0) CustomAccessibilityAction(moveUpLabel) { move(i, i - 1); true } else null,
                            if (i < items.lastIndex) CustomAccessibilityAction(moveDownLabel) { move(i, i + 1); true } else null
                        )
                    }
                }
                .pointerInput(enabled, i, items.size) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { dragging = i; offset = 0f },
                        onDrag = { change, amount -> change.consume(); offset += amount.y },
                        onDragEnd = {
                            val to = dragTarget(i, offset, List(items.size) { heights[it] ?: 0 })
                            dragging = null
                            offset = 0f
                            if (to != i) move(i, to)
                        },
                        onDragCancel = { dragging = null; offset = 0f }
                    )
                }
            Box(
                Modifier
                    .onSizeChanged { heights[i] = it.height }
                    .zIndex(if (i == from) 1f else 0f)
                    .graphicsLayer { translationY = shift }
            ) {
                row(i, item, handle)
            }
        }
    }
}

/** The handle a row is dragged by, with [modifier] from [DragReorderColumn]. */
@Composable
fun DragHandle(modifier: Modifier, enabled: Boolean) {
    val description = stringResource(Res.string.cd_reorder)
    Box(modifier.size(36.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Icon(painterResource(Res.drawable.ic_drag_handle), null, tint = DeepCarbon.copy(alpha = if (enabled) 0.45f else 0.2f), modifier = Modifier.size(22.dp))
    }
}
