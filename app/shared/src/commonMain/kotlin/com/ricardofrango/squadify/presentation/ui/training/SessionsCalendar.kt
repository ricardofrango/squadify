package com.ricardofrango.squadify.presentation.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.Country
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.mondayOf
import com.ricardofrango.squadify.model.publicHolidaysBetween
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.cd_next
import squadify.app.shared.generated.resources.cd_previous
import squadify.app.shared.generated.resources.ic_chevron_left
import squadify.app.shared.generated.resources.ic_chevron_right
import squadify.app.shared.generated.resources.legend_cancelled
import squadify.app.shared.generated.resources.legend_holiday
import squadify.app.shared.generated.resources.legend_needs_ratings
import squadify.app.shared.generated.resources.legend_session

/** The 6 Monday-first weeks shown for [month] (its 1st), as dates. */
internal fun calendarDays(month: LocalDate): List<LocalDate> {
    val first = mondayOf(month)
    return (0 until 42).map { first.plus(DatePeriod(days = it)) }
}

/**
 * The Training tab's calendar (design 39a): Monday-first month grid with a dot per session (amber when it
 * needs ratings, grey when cancelled) and national holidays' day numbers in amber. Tapping a day selects it.
 */
@Composable
fun SessionsCalendar(
    month: LocalDate,
    sessions: List<TrainingSession>,
    country: Country,
    selected: LocalDate?,
    now: LocalDateTime,
    poolSize: Int?,
    canRate: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onMonthChange: (LocalDate) -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val days = calendarDays(month)
    val holidays = publicHolidaysBetween(country, days.first(), days.last())
    val byDate = sessions.groupBy { it.date }
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${monthName(month)} ${month.year}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, modifier = Modifier.weight(1f))
            IconButton(onClick = { onMonthChange(month.minus(DatePeriod(months = 1))) }, enabled = canGoBack) {
                Icon(painterResource(Res.drawable.ic_chevron_left), stringResource(Res.string.cd_previous), tint = DeepCarbon.copy(alpha = if (canGoBack) 0.7f else 0.2f))
            }
            IconButton(onClick = { onMonthChange(month.plus(DatePeriod(months = 1))) }, enabled = canGoForward) {
                Icon(painterResource(Res.drawable.ic_chevron_right), stringResource(Res.string.cd_next), tint = DeepCarbon.copy(alpha = if (canGoForward) 0.7f else 0.2f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (1..7).forEach { day ->
                Text(weekdayShort(day).take(1), style = MaterialTheme.typography.labelSmall, color = StatsSecondaryText, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(4.dp))
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = date.month == month.month
                    val isSelected = date == selected
                    Column(
                        Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                            .background(if (isSelected) PitchGreen.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onSelect(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            date.day.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (date == now.date) FontWeight.Black else FontWeight.Normal),
                            color = when {
                                !inMonth -> DeepCarbon.copy(alpha = 0.25f)
                                date in holidays -> AmberText
                                else -> DeepCarbon
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            byDate[date].orEmpty().take(3).forEach { s ->
                                val color = when (chipFor(s, now, null, poolSize, canRate)) {
                                    SessionChip.CANCELLED -> CarbonGray.copy(alpha = 0.35f)
                                    SessionChip.NEEDS_RATINGS -> Amber
                                    else -> PitchGreen
                                }
                                Box(Modifier.size(5.dp).background(color, CircleShape))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(stringResource(Res.string.legend_session), PitchGreen)
            if (canRate) Legend(stringResource(Res.string.legend_needs_ratings), Amber)
            Legend(stringResource(Res.string.legend_cancelled), CarbonGray.copy(alpha = 0.35f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("5", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = AmberText)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.legend_holiday), style = MaterialTheme.typography.labelSmall, color = StatsSecondaryText)
            }
        }
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = StatsSecondaryText)
    }
}
