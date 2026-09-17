package com.ricardofrango.squadify.presentation.ui.clubdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.*
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(16.dp)

/**
 * The club Home tab (design screen 2A): a 2x2 stat grid, a "Next up" list with one row per team that
 * has an upcoming match, and role-scoped "Needs you" alerts. All the data comes pre-computed in
 * [ClubHome] from `GetClubHomeUseCase`.
 */
@Composable
fun HomeTab(
    home: ClubHome?,
    onRetry: () -> Unit,
    onNavigateToAgeGroup: (ageGroupId: String) -> Unit,
    onNavigateToMatch: (matchId: String) -> Unit
) {
    if (home == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(Res.string.msg_failed_to_load_home),
                style = MaterialTheme.typography.bodyMedium,
                color = DeepCarbon.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        home.seasonName?.let { season ->
            Text(
                "${stringResource(roleLabel(home.role))} · $season",
                style = MaterialTheme.typography.bodySmall,
                color = DeepCarbon.copy(alpha = 0.5f)
            )
        }

        StatGrid(home)

        // ---- Next up ----
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(Res.string.label_home_next_up))
            if (home.nextUp.isEmpty()) {
                Text(
                    stringResource(Res.string.msg_home_no_next_up),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeepCarbon.copy(alpha = 0.5f)
                )
            } else {
                home.nextUp.forEach { row -> NextUpRow(row, onClick = { onNavigateToMatch(row.matchId) }) }
            }
        }

        // ---- Needs you ----
        if (home.alerts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(stringResource(Res.string.label_home_needs_you))
                home.alerts.forEach { alert ->
                    AlertRow(
                        alert = alert,
                        onClick = alert.ageGroupId?.let { id -> { onNavigateToAgeGroup(id) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatGrid(home: ClubHome) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(home.teamCount.toString(), stringResource(Res.string.tab_teams), Modifier.weight(1f))
            StatCard(home.playerCount.toString(), stringResource(Res.string.tab_players), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(home.coachCount.toString(), stringResource(Res.string.label_home_coaches), Modifier.weight(1f))
            StatCard(
                home.matchesThisWeek.toString(),
                stringResource(Res.string.label_home_matches_this_week),
                Modifier.weight(1f),
                highlight = true
            )
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
    Surface(
        modifier = modifier.height(96.dp),
        shape = CardShape,
        color = if (highlight) PitchGreen else Color.White,
        border = if (highlight) null else BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = if (highlight) Color.White else DeepCarbon
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) Color.White.copy(alpha = 0.9f) else DeepCarbon.copy(alpha = 0.55f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = DeepCarbon
    )
}

@Composable
private fun NextUpRow(item: ClubHomeNextUp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DateBlock(item.dateTime)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${item.teamName} · ${item.opponentName}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(timeLabel(item.dateTime), item.location?.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.55f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            CallUpPill(calledUp = item.callUpsTotal)
        }
    }
}

@Composable
private fun CallUpPill(calledUp: Int) {
    Surface(shape = RoundedCornerShape(8.dp), color = CarbonGray.copy(alpha = 0.12f)) {
        Text(
            calledUp.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun AlertRow(alert: ClubHomeAlert, onClick: (() -> Unit)?) {
    val text = when (alert.type) {
        ClubHomeAlertType.AGE_GROUP_NEEDS_COACH ->
            stringResource(Res.string.msg_home_needs_coach, alert.ageGroupName ?: "")
        ClubHomeAlertType.CALLUPS_UNSENT ->
            stringResource(
                Res.string.msg_home_callups_unsent,
                alert.count,
                alert.date?.let { stringResource(weekdayLabel(it.dayOfWeek)) } ?: ""
            )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            ),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(PitchGreen, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = DeepCarbon, modifier = Modifier.weight(1f),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (onClick != null) {
                Text(" ›", color = DeepCarbon.copy(alpha = 0.35f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DateBlock(instant: Instant) {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
        Text(
            dt.dayOfWeek.name.take(3),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = PitchGreen
        )
        Text(
            dt.day.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon
        )
    }
}

private fun timeLabel(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

private fun roleLabel(role: ClubRole): StringResource = when (role) {
    ClubRole.CLUB_ADMIN -> Res.string.label_role_club_admin
    ClubRole.COORDINATOR -> Res.string.label_role_coordinator
    ClubRole.MEMBER -> Res.string.label_role_member
}

private fun weekdayLabel(day: DayOfWeek): StringResource = when (day) {
    DayOfWeek.MONDAY -> Res.string.weekday_monday
    DayOfWeek.TUESDAY -> Res.string.weekday_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.weekday_wednesday
    DayOfWeek.THURSDAY -> Res.string.weekday_thursday
    DayOfWeek.FRIDAY -> Res.string.weekday_friday
    DayOfWeek.SATURDAY -> Res.string.weekday_saturday
    DayOfWeek.SUNDAY -> Res.string.weekday_sunday
    else -> Res.string.weekday_monday
}

// ---- Preview ----

@Preview
@Composable
private fun HomeTabPreview() {
    val club = Club("c1", "CD Ribamar", status = ClubStatus.ACTIVE, sport = SportType.FOOTBALL, createdAt = Instant.fromEpochMilliseconds(0))
    val base = Instant.fromEpochMilliseconds(1_760_000_000_000)
    SquadifyTheme {
        HomeTab(
            home = ClubHome(
                club = club,
                seasonName = "26/27",
                role = ClubRole.CLUB_ADMIN,
                teamCount = 7, playerCount = 118, coachCount = 9, matchesThisWeek = 5,
                nextUp = listOf(
                    ClubHomeNextUp("m1", "t1", "Sub-15 A", "AD Fontelas", "Campo do Ribamar", base, 18),
                    ClubHomeNextUp("m2", "t2", "Sub-17 A", "GD Marinhas", "Away", base, 0)
                ),
                alerts = listOf(
                    ClubHomeAlert(ClubHomeAlertType.CALLUPS_UNSENT, count = 2, date = kotlinx.datetime.LocalDate(2026, 9, 12)),
                    ClubHomeAlert(ClubHomeAlertType.AGE_GROUP_NEEDS_COACH, count = 1, ageGroupId = "ag-x", ageGroupName = "Sub-11")
                )
            ),
            onRetry = {},
            onNavigateToAgeGroup = {},
            onNavigateToMatch = {}
        )
    }
}
