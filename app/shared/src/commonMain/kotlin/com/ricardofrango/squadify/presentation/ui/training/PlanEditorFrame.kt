package com.ricardofrango.squadify.presentation.ui.training

import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_saving_changes

/**
 * The frame of every plan editor (design 40): top bar, an optional dark context strip, the scrolling
 * form and a pinned Save. Leaving with unsaved changes (back arrow or system back) asks first.
 */
@Composable
fun PlanEditorFrame(
    title: String,
    subtitle: String?,
    state: PlanEditUiState,
    saveText: String,
    canSave: Boolean,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    contextStrip: @Composable () -> Unit = {},
    // [wide]: landscape or tablet, where the editors use two columns.
    content: @Composable (wide: Boolean) -> Unit
) {
    var askDiscard by remember { mutableStateOf(false) }
    val leave = { if (state.dirty) askDiscard = true else onNavigateBack() }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.dirty,
        onBackCompleted = { askDiscard = true }
    )
    Scaffold(
        containerColor = Color.White,
        topBar = { SquadifyTopBar(title = title, subtitle = subtitle, onNavigateBack = leave) }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().background(Color.White)
        if (state.loading || state.loadFailed) {
            PlanLoadingOrError(state.loading, modifier, onRetry)
            return@Scaffold
        }
        Column(modifier) {
            contextStrip()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 600.dp
                // The error line sits inside the scroll, so its space scrolls with the form instead of
                // staying fixed above Save.
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = ScreenPadding.Horizontal, end = ScreenPadding.Horizontal, top = 8.dp, bottom = 12.dp)) {
                    content(wide)
                    FormErrorLine(state.error)
                }
            }
            Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), contentAlignment = Alignment.CenterEnd) {
                    PrimaryLoadingButton(
                        text = saveText,
                        submittingText = stringResource(Res.string.btn_saving_changes),
                        isLoading = state.saving,
                        enabled = canSave,
                        onClick = onSave,
                        modifier = Modifier.widthIn(max = 420.dp)
                    )
                }
            }
        }
    }
    if (askDiscard) {
        DiscardChangesDialog(onDiscard = { askDiscard = false; onNavigateBack() }, onKeepEditing = { askDiscard = false })
    }
}

/** Two columns on a wide screen, one otherwise (the editors' landscape layout, design 40h). */
@Composable
fun EditorColumns(wide: Boolean, left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    if (wide) {
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp), content = left)
            Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp), content = right)
        }
    } else {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
            left()
            right()
        }
    }
}
