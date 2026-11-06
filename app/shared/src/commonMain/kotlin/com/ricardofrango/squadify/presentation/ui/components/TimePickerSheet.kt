package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_confirm
import squadify.app.shared.generated.resources.label_select_kickoff_time
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The time counterpart to [DatePickerSheet]: a modal bottom sheet with a 24-hour Material
 * [TimePicker] and one confirm button. Used by the Schedule Match screen for the kick-off time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(
    title: StringResource,
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            // Scrolls when it doesn't fit, on a phone lying down.
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(title),
                modifier = Modifier.fillMaxWidth(),
                color = DeepCarbon.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Start
            )
            TimePicker(state = timePickerState, colors = squadifyTimePickerColors())
            Button(
                onClick = { onConfirm(LocalTime(timePickerState.hour, timePickerState.minute)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PitchGreen,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(Res.string.btn_confirm))
            }
        }
    }
}

@Preview
@Composable
private fun TimePickerSheetPreview() {
    SquadifyTheme {
        TimePickerSheet(
            title = Res.string.label_select_kickoff_time,
            initialTime = LocalTime(10, 30),
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun TimePickerSheetLandscapePreview() {
    SquadifyTheme {
        TimePickerSheet(
            title = Res.string.label_select_kickoff_time,
            initialTime = LocalTime(10, 30),
            onConfirm = {},
            onDismiss = {}
        )
    }
}

// A phone in dark mode: the picker keeps the app's light colours.
@Preview
@Composable
private fun TimePickerSheetDarkModePreview() {
    SquadifyTheme(darkTheme = true) {
        TimePickerSheet(
            title = Res.string.label_select_kickoff_time,
            initialTime = LocalTime(10, 30),
            onConfirm = {},
            onDismiss = {}
        )
    }
}
