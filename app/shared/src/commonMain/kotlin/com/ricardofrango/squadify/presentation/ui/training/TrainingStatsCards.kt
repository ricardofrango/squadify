package com.ricardofrango.squadify.presentation.ui.training

import com.ricardofrango.squadify.presentation.ui.components.twoDecimals
import com.ricardofrango.squadify.presentation.ui.components.PlayerBadge
import com.ricardofrango.squadify.presentation.ui.theme.IsClubThemed
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.AgeGroupTrainingStats
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.PlayerTrainingSeason
import com.ricardofrango.squadify.model.monthStartOf
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.col_attended
import squadify.app.shared.generated.resources.col_avg
import squadify.app.shared.generated.resources.col_player
import squadify.app.shared.generated.resources.label_all_season
import squadify.app.shared.generated.resources.label_attendance_tile
import squadify.app.shared.generated.resources.label_avg_tile
import squadify.app.shared.generated.resources.label_sort
import squadify.app.shared.generated.resources.label_training_season_staff
import squadify.app.shared.generated.resources.label_training_staff
import squadify.app.shared.generated.resources.msg_absent_not_averaged
import squadify.app.shared.generated.resources.msg_no_training_stats
import squadify.app.shared.generated.resources.sort_attendance
import squadify.app.shared.generated.resources.sort_name
import squadify.app.shared.generated.resources.sort_rating

// Training stats (design 39j), staff only: attendance = sessions rated above 0 out of the ended,
// non-cancelled ones in the period; the average leaves Absent out. Below 75% attendance is amber.

enum class TrainingSort { ATTENDANCE, RATING, NAME }

data class TrainingStatsRow(val player: Player, val attended: Int, val sessions: Int, val average: Double?) {
    val percent: Int get() = if (sessions == 0) 0 else attended * 100 / sessions
}

/** Below this attendance a row is amber. */
const val LOW_ATTENDANCE_PERCENT = 75

/** The months that have sessions, newest first, for the period filter. */
internal fun trainingMonths(stats: AgeGroupTrainingStats): List<LocalDate> =
    stats.sessions.map { monthStartOf(it.date) }.distinct().sortedDescending()

/** One row per pool player for the period ([month] null = the whole season), sorted. */
internal fun trainingTable(stats: AgeGroupTrainingStats, month: LocalDate?, sort: TrainingSort): List<TrainingStatsRow> {
    val sessions = stats.sessions.filter { month == null || monthStartOf(it.date) == month }
    val ids = sessions.map { it.id }.toSet()
    val ratings = stats.ratings.filterKeys { it in ids }.values.flatten().groupBy({ it.playerId }, { it.rating })
    val rows = stats.players.map { p ->
        val mine = ratings[p.id].orEmpty()
        val trained = mine.filter { it > 0.0 }
        TrainingStatsRow(p, trained.size, sessions.size, trained.takeIf { it.isNotEmpty() }?.average())
    }
    return when (sort) {
        TrainingSort.ATTENDANCE -> rows.sortedWith(compareByDescending<TrainingStatsRow> { it.attended }.thenByDescending { it.average ?: -1.0 })
        TrainingSort.RATING -> rows.sortedWith(compareByDescending<TrainingStatsRow> { it.average ?: -1.0 }.thenByDescending { it.attended })
        TrainingSort.NAME -> rows.sortedBy { "${it.player.firstName} ${it.player.lastName}".lowercase() }
    }
}

/** Player Details: attendance and average rating for one season (two tiles). */
@Composable
fun PlayerTrainingCard(season: PlayerTrainingSeason) {
    StatsCard {
        StatsHeading(stringResource(Res.string.label_training_season_staff, season.seasonName), Modifier.padding(bottom = 10.dp))
        val percent = if (season.sessions == 0) 0 else season.attended * 100 / season.sessions
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("$percent%", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = if (season.sessions > 0 && percent < LOW_ATTENDANCE_PERCENT) AmberText else SuccessGreen)
                Text(stringResource(Res.string.label_attendance_tile, season.attended, season.sessions), style = MaterialTheme.typography.labelMedium, color = StatsSecondaryText)
            }
            Column(Modifier.weight(1f).background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text(season.average?.twoDecimals() ?: "—", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = season.average?.let { ratingColor(it) } ?: StatsSecondaryText)
                Text(stringResource(Res.string.label_avg_tile, season.attended), style = MaterialTheme.typography.labelMedium, color = StatsSecondaryText)
            }
        }
        Text(stringResource(Res.string.msg_absent_not_averaged), style = MaterialTheme.typography.labelSmall, color = StatsSecondaryText, modifier = Modifier.padding(top = 6.dp))
    }
}
