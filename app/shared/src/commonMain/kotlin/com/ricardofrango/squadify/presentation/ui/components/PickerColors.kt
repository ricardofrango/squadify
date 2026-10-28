package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.SectionBorderColor

// The date and time pickers in the app's own colours, as its sheets: white, carbon text, and the
// club's accent ([PitchGreen]) for what's selected. Material's defaults follow the phone's dark mode,
// which drew a dark calendar or clock in the middle of a white sheet.

/** For DatePicker and DateRangePicker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun squadifyDatePickerColors(): DatePickerColors = DatePickerDefaults.colors(
    containerColor = Color.White,
    titleContentColor = DeepCarbon.copy(alpha = 0.6f),
    headlineContentColor = DeepCarbon,
    weekdayContentColor = DeepCarbon.copy(alpha = 0.55f),
    subheadContentColor = DeepCarbon.copy(alpha = 0.6f),
    navigationContentColor = DeepCarbon,
    yearContentColor = DeepCarbon,
    disabledYearContentColor = DeepCarbon.copy(alpha = 0.3f),
    currentYearContentColor = PitchGreen,
    selectedYearContentColor = Color.White,
    selectedYearContainerColor = PitchGreen,
    dayContentColor = DeepCarbon,
    disabledDayContentColor = DeepCarbon.copy(alpha = 0.3f),
    selectedDayContentColor = Color.White,
    selectedDayContainerColor = PitchGreen,
    todayContentColor = PitchGreen,
    todayDateBorderColor = PitchGreen,
    dayInSelectionRangeContentColor = DeepCarbon,
    dayInSelectionRangeContainerColor = PitchGreen.copy(alpha = 0.12f),
    dividerColor = SectionBorderColor
)

/** For TimePicker: a light grey dial and time boxes, the selected one in the accent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun squadifyTimePickerColors(): TimePickerColors = TimePickerDefaults.colors(
    containerColor = Color.White,
    clockDialColor = ScreenBackground,
    clockDialSelectedContentColor = Color.White,
    clockDialUnselectedContentColor = DeepCarbon,
    selectorColor = PitchGreen,
    periodSelectorBorderColor = SectionBorderColor,
    periodSelectorSelectedContainerColor = PitchGreen.copy(alpha = 0.12f),
    periodSelectorUnselectedContainerColor = Color.White,
    periodSelectorSelectedContentColor = PitchGreen,
    periodSelectorUnselectedContentColor = DeepCarbon,
    timeSelectorSelectedContainerColor = PitchGreen.copy(alpha = 0.12f),
    timeSelectorUnselectedContainerColor = ScreenBackground,
    timeSelectorSelectedContentColor = PitchGreen,
    timeSelectorUnselectedContentColor = DeepCarbon
)
