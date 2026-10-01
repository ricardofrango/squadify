package com.ricardofrango.squadify.presentation.ui.components

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.home_days_ago
import squadify.app.shared.generated.resources.home_today
import squadify.app.shared.generated.resources.home_yesterday
import squadify.app.shared.generated.resources.month_short_1
import squadify.app.shared.generated.resources.month_short_10
import squadify.app.shared.generated.resources.month_short_11
import squadify.app.shared.generated.resources.month_short_12
import squadify.app.shared.generated.resources.month_short_2
import squadify.app.shared.generated.resources.month_short_3
import squadify.app.shared.generated.resources.month_short_4
import squadify.app.shared.generated.resources.month_short_5
import squadify.app.shared.generated.resources.month_short_6
import squadify.app.shared.generated.resources.month_short_7
import squadify.app.shared.generated.resources.month_short_8
import squadify.app.shared.generated.resources.month_short_9

private val MonthShort: List<StringResource> = listOf(
    Res.string.month_short_1, Res.string.month_short_2, Res.string.month_short_3, Res.string.month_short_4,
    Res.string.month_short_5, Res.string.month_short_6, Res.string.month_short_7, Res.string.month_short_8,
    Res.string.month_short_9, Res.string.month_short_10, Res.string.month_short_11, Res.string.month_short_12
)

/** "27 Sep" / "27 set." -- a short, localized day-and-month label. */
@Composable
fun shortDateLabel(date: LocalDate): String = "${date.day} ${stringResource(MonthShort[date.month.ordinal])}"

/** "today", "yesterday", or "3 days ago" (localized), counting from [date] to [today]. */
@Composable
fun daysAgoLabel(date: LocalDate, today: LocalDate): String {
    val days = date.daysUntil(today)
    return when {
        days <= 0 -> stringResource(Res.string.home_today)
        days == 1 -> stringResource(Res.string.home_yesterday)
        else -> pluralStringResource(Res.plurals.home_days_ago, days, days)
    }
}
