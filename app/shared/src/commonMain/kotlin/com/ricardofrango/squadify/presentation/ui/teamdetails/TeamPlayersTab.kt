package com.ricardofrango.squadify.presentation.ui.teamdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.BenchEntry
import com.ricardofrango.squadify.model.SquadEntry
import com.ricardofrango.squadify.model.SquadSort
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.presentation.ui.components.RatingPill
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.labelRes
import com.ricardofrango.squadify.presentation.ui.components.oneDecimal
import com.ricardofrango.squadify.presentation.ui.matchreport.MiniPill
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.DeepGreen
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*

// The Members tab of Team Details (design 32b, "Players" there -- renamed since it also holds the staff): the players who actually played for this team, the
// ones called up without coming on in a collapsed group under them, then the age group's staff and
// pool as context.

/**
 * Apps (default) · Minutes · Goals · Rating (staff only) · Name, on one line that scrolls sideways
 * when it doesn't fit (e.g. Portuguese at 360dp) instead of wrapping onto a second row.
 */
@Composable
internal fun SquadSortChips(selected: SquadSort, staff: Boolean, onSelected: (SquadSort) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(Res.string.squad_sort),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
            color = StatsSecondaryText,
            modifier = Modifier.padding(end = 2.dp)
        )
        SquadSort.entries.filter { staff || it != SquadSort.RATING }.forEach { sort ->
            val isSelected = sort == selected
            Surface(
                onClick = { onSelected(sort) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) CarbonGray else Color.White,
                border = BorderStroke(1.5.dp, if (isSelected) CarbonGray else CarbonGray.copy(alpha = 0.16f))
            ) {
                Box(Modifier.heightIn(min = 32.dp).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(sort.labelRes()),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                        color = if (isSelected) Color.White else DeepCarbon
                    )
                }
            }
        }
    }
}

private fun SquadSort.labelRes() = when (this) {
    SquadSort.APPS -> Res.string.squad_sort_apps
    SquadSort.MINUTES -> Res.string.squad_sort_minutes
    SquadSort.GOALS -> Res.string.squad_sort_goals
    SquadSort.RATING -> Res.string.squad_sort_rating
    SquadSort.NAME -> Res.string.squad_sort_name
}

/** The used players, then the collapsed "Called up, didn't play" group, in one card. */
@Composable
internal fun SquadCard(
    used: List<SquadEntry>,
    bench: List<BenchEntry>,
    staff: Boolean,
    onOpenPlayer: (String) -> Unit
) {
    var benchOpen by remember { mutableStateOf(false) }
    StatsCard(contentPadding = 0.dp) {
        used.forEachIndexed { i, entry ->
            if (i > 0) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
            SquadRow(entry, staff) { onOpenPlayer(entry.player.id) }
        }
        if (bench.isNotEmpty()) {
            if (used.isNotEmpty()) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFFAFAFA)).clickable { benchOpen = !benchOpen }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(Res.string.squad_bench_group, bench.size),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                    color = StatsSecondaryText,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painterResource(Res.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(18.dp).rotate(if (benchOpen) 180f else 0f)
                )
            }
            if (benchOpen) {
                bench.forEach { b ->
                    HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
                    BenchRow(b) { onOpenPlayer(b.player.id) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SquadRow(entry: SquadEntry, staff: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShirtNumber(entry.player.shirtNumber, muted = false)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.player.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp), color = DeepCarbon)
            Spacer(Modifier.height(3.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                entry.player.position?.let {
                    Text(stringResource(it.labelRes()), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = StatsSecondaryText)
                }
                Text(
                    // The minutes' apostrophe is appended here: the resource generator doesn't reliably
                    // unescape \' in a formatted string (same as MatchRow).
                    "${pluralStringResource(Res.plurals.squad_apps, entry.apps, entry.apps)} · ${entry.minutes}'",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                    color = DeepCarbon
                )
                if (entry.goals > 0) MiniPill(stringResource(Res.string.report_chip_goals, entry.goals), PitchGreen.copy(alpha = 0.12f), DeepGreen)
                if (entry.assists > 0) MiniPill(stringResource(Res.string.report_chip_assists, entry.assists), CarbonGray.copy(alpha = 0.07f), DeepCarbon)
            }
        }
        if (staff) {
            Spacer(Modifier.width(11.dp))
            val avg = entry.averageRating
            if (avg != null) {
                RatingPill(avg, avg.oneDecimal(), minWidth = 38.dp, height = 32.dp, fontSize = 14.5.sp, corner = 9.dp)
            } else {
                Box(Modifier.size(width = 38.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                    Text("—", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = StatsSecondaryText)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null, tint = Color(0xFFBDBDBD), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun BenchRow(entry: BenchEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShirtNumber(entry.player.shirtNumber, muted = true)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.player.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = DeepCarbon.copy(alpha = 0.7f))
            Text(stringResource(Res.string.squad_bench_row, entry.calledUp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = StatsSecondaryText)
        }
    }
}

@Composable
private fun ShirtNumber(number: Int?, muted: Boolean) {
    Text(
        number?.toString() ?: "—",
        modifier = Modifier.width(24.dp),
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 16.sp),
        color = if (muted) DeepCarbon.copy(alpha = 0.35f) else PitchGreen
    )
}

/**
 * The age group's staff and pool, as context under the Members tab (design 32): both belong to the age
 * group, not the team, so they left the header strip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgeGroupContextCard(
    staff: List<AgeGroupStaffMember>,
    ageGroupName: String,
    poolSize: Int,
    siblingTeamNames: List<String>,
    onViewPool: () -> Unit
) {
    StatsCard(contentPadding = 0.dp) {
        if (staff.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                StatsHeading(stringResource(Res.string.squad_staff_title), Modifier.padding(bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    staff.forEach { member ->
                        val role = stringResource(if (member.role == TeamRole.COACH) Res.string.label_role_coach else Res.string.label_role_staff)
                        Surface(shape = RoundedCornerShape(8.dp), color = CarbonGray.copy(alpha = 0.06f)) {
                            Text(
                                "${member.firstName.take(1)}. ${member.lastName} · $role",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                color = DeepCarbon
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onViewPool).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.squad_pool_title, ageGroupName, poolSize),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                    color = DeepCarbon
                )
                if (siblingTeamNames.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.squad_pool_shared, siblingTeamNames.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = StatsSecondaryText
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(stringResource(Res.string.squad_view_pool), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), color = PitchGreen)
        }
    }
}
