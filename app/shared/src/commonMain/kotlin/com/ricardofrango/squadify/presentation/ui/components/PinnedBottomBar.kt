package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme

/**
 * A form's main button pinned to the bottom of the screen, for a `Scaffold`'s `bottomBar`: the
 * screen's grey, a shadow over the content scrolling under it, 12dp above and below the button, and
 * the navigation bar's inset under that (a `bottomBar` gets none from the `Scaffold`). The same as
 * Match details' bar. An error goes above the button, in [content]. On a wide screen the button
 * keeps the width of a form ([readableWidth]), centred, rather than stretching across the window.
 * Hidden while typing on a phone lying down ([keyboardOnShortWindow]).
 */
@Composable
fun PinnedBottomBar(content: @Composable ColumnScope.() -> Unit) {
    if (keyboardOnShortWindow()) return
    Surface(color = ScreenBackground, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                // Above the navigation bar, and clear of the camera cutout on a phone lying down.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .readableWidth()
                .padding(horizontal = ScreenPadding.Horizontal, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Preview
@Composable
private fun PinnedBottomBarPreview() {
    SquadifyTheme { PinnedBottomBar { PrimaryLoadingButton(text = "Add 2 players", submittingText = "Adding…", isLoading = false, onClick = {}) } }
}

@Preview(widthDp = 900, heightDp = 200)
@Composable
private fun PinnedBottomBarLandscapePreview() {
    SquadifyTheme { PinnedBottomBar { PrimaryLoadingButton(text = "Add 2 players", submittingText = "Adding…", isLoading = false, onClick = {}) } }
}
