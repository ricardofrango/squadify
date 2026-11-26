package com.sportssoftw.squadcall.presentation.ui.training

import androidx.compose.runtime.Composable
import com.sportssoftw.squadcall.model.Holiday
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import squadcall.app.shared.generated.resources.Res
import squadcall.app.shared.generated.resources.holiday_all_saints
import squadcall.app.shared.generated.resources.holiday_assumption
import squadcall.app.shared.generated.resources.holiday_christmas
import squadcall.app.shared.generated.resources.holiday_corpus_christi
import squadcall.app.shared.generated.resources.holiday_easter
import squadcall.app.shared.generated.resources.holiday_freedom_day
import squadcall.app.shared.generated.resources.holiday_good_friday
import squadcall.app.shared.generated.resources.holiday_immaculate_conception
import squadcall.app.shared.generated.resources.holiday_labour_day
import squadcall.app.shared.generated.resources.holiday_new_year
import squadcall.app.shared.generated.resources.holiday_portugal_day
import squadcall.app.shared.generated.resources.holiday_republic_day
import squadcall.app.shared.generated.resources.holiday_restoration
import squadcall.app.shared.generated.resources.wd_short_1
import squadcall.app.shared.generated.resources.wd_short_2
import squadcall.app.shared.generated.resources.wd_short_3
import squadcall.app.shared.generated.resources.wd_short_4
import squadcall.app.shared.generated.resources.wd_short_5
import squadcall.app.shared.generated.resources.wd_short_6
import squadcall.app.shared.generated.resources.wd_short_7

// Labels shared by the training session screens (design 39).

private val WeekdayShort = listOf(
    Res.string.wd_short_1, Res.string.wd_short_2, Res.string.wd_short_3, Res.string.wd_short_4,
    Res.string.wd_short_5, Res.string.wd_short_6, Res.string.wd_short_7
)

/** "Mon" for ISO day 1. */
@Composable
fun weekdayShort(isoDay: Int): String = stringResource(WeekdayShort[(isoDay - 1).coerceIn(0, 6)])

/** "18:00". */
fun LocalTime.hhmm(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** "18:00–19:00". */
fun timeRange(start: LocalTime, end: LocalTime): String = "${start.hhmm()}–${end.hhmm()}"

private fun Holiday.nameRes(): StringResource = when (this) {
    Holiday.NEW_YEAR -> Res.string.holiday_new_year
    Holiday.GOOD_FRIDAY -> Res.string.holiday_good_friday
    Holiday.EASTER -> Res.string.holiday_easter
    Holiday.FREEDOM_DAY -> Res.string.holiday_freedom_day
    Holiday.LABOUR_DAY -> Res.string.holiday_labour_day
    Holiday.CORPUS_CHRISTI -> Res.string.holiday_corpus_christi
    Holiday.PORTUGAL_DAY -> Res.string.holiday_portugal_day
    Holiday.ASSUMPTION -> Res.string.holiday_assumption
    Holiday.REPUBLIC_DAY -> Res.string.holiday_republic_day
    Holiday.ALL_SAINTS -> Res.string.holiday_all_saints
    Holiday.RESTORATION_OF_INDEPENDENCE -> Res.string.holiday_restoration
    Holiday.IMMACULATE_CONCEPTION -> Res.string.holiday_immaculate_conception
    Holiday.CHRISTMAS -> Res.string.holiday_christmas
}

@Composable
fun holidayName(holiday: Holiday): String = stringResource(holiday.nameRes())
