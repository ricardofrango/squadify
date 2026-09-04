package com.ricardofrango.footballmanager.presentation.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.footballmanager.presentation.ui.theme.FootballManagerTheme
import com.ricardofrango.footballmanager.presentation.ui.theme.PitchGreen
import footballmanager.app.shared.generated.resources.Res
import footballmanager.app.shared.generated.resources.btn_confirm
import footballmanager.app.shared.generated.resources.label_select_start_date
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * @param minDate When set, dates before it can't be selected in the picker -- e.g. pass the season's
 *   current start date when picking the end date, so it's not possible to pick an end date before the
 *   start date (and vice versa for [maxDate] on the start-date picker). Both bounds are inclusive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    title: StringResource,
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val minMillis = remember(minDate) { minDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds() }
    val maxMillis = remember(maxDate) { maxDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds() }
    val selectableDates = remember(minMillis, maxMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                (minMillis == null || utcTimeMillis >= minMillis) && (maxMillis == null || utcTimeMillis <= maxMillis)
        }
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        selectableDates = selectableDates
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        DatePicker(
            state = datePickerState,
            showModeToggle = false,
            title = {
                Text(
                    text = stringResource(title),
                    modifier = Modifier.padding(
                        PaddingValues(
                            start = 24.dp,
                            end = 12.dp,
                            top = 16.dp
                        )
                    ),
                    color = DatePickerDefaults.colors().titleContentColor,
                )
            }
        )
        Button(
            onClick = {
                val millis = datePickerState.selectedDateMillis
                if (millis != null) {
                    onConfirm(
                        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                    )
                } else {
                    onDismiss()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PitchGreen,
                contentColor = Color.White
            )
        ) {
            Text(stringResource(Res.string.btn_confirm))
        }
    }
}

@Preview
@Composable
private fun CreateContentPreviewPortrait() {
    FootballManagerTheme {
        DatePickerSheet(
            title = Res.string.label_select_start_date,
            initialDate = LocalDate(2023, 1, 1),
            onConfirm = {},
            onDismiss = {}
        )
    }
}