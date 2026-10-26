package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen

// Lists fetched a page at a time (TECHNICAL_DECISIONS §5): the next page loads on its own as the list
// nears its end, with a spinner under the last row meanwhile.

/**
 * Calls [onLoadMore] once the list is scrolled to within [threshold] rows of its end, while [enabled]
 * (more to load and nothing loading). A page too short to fill the screen asks for the next straight
 * away, since its end is already in sight.
 */
@Composable
fun LoadMoreEffect(listState: LazyListState, enabled: Boolean, threshold: Int = 5, onLoadMore: () -> Unit) {
    val load by rememberUpdatedState(onLoadMore)
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf info.totalItemsCount == 0
            last >= info.totalItemsCount - 1 - threshold
        }
    }
    LaunchedEffect(nearEnd, enabled) { if (nearEnd && enabled) load() }
}

/** The spinner under the last row while the next page loads. */
fun LazyListScope.loadingMoreItem(visible: Boolean) {
    if (!visible) return
    item(key = "loading-more") {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), color = PitchGreen, strokeWidth = 2.5.dp)
        }
    }
}
