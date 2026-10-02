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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.*
import com.ricardofrango.squadify.presentation.ui.components.daysAgoLabel
import com.ricardofrango.squadify.presentation.ui.components.shortDateLabel
import com.ricardofrango.squadify.presentation.ui.theme.Amber
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
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

        // Landscape: Next up and Needs you side by side (27d); portrait: stacked.
        BoxWithConstraints {
            val wide = maxWidth > 600.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    NextUpSection(home, onNavigateToMatch, Modifier.weight(1f))
                    NeedsYouSection(home, onNavigateToAgeGroup, onNavigateToMatch, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    NextUpSection(home, onNavigateToMatch)
                    NeedsYouSection(home, onNavigateToAgeGroup, onNavigateToMatch)
                }
            }
        }
    }
}

@Composable
private fun NextUpSection(home: ClubHome, onNavigateToMatch: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
}

/**
 * "Needs you" (design 27): one card, rows split by hairlines. Time-bound items first (call-ups), then
 * pending reports oldest first (the server already sends them in that order), then configuration
 * gaps. 1-2 pending reports are normal rows; from 3 up they collapse into one expandable group row.
 */
@Composable
private fun NeedsYouSection(
    home: ClubHome,
    onNavigateToAgeGroup: (String) -> Unit,
    onNavigateToMatch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (home.alerts.isEmpty()) return
    val callUps = home.alerts.filter { it.type == ClubHomeAlertType.CALLUPS_UNSENT }
    val reports = home.alerts.filter { it.type == ClubHomeAlertType.MATCH_REPORT_PENDING }
    val gaps = home.alerts.filter { it.type == ClubHomeAlertType.AGE_GROUP_NEEDS_COACH }
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Which call-up days (by date) are expanded into their matches.
    val expandedCallUps = remember { mutableStateListOf<String>() }

    // Each entry is one row of the card; dividers go between them.
    val rows = buildList<@Composable () -> Unit> {
        // A call-up to-do opens its match; a day with several matches expands into one row per match.
        callUps.forEach { alert ->
            val key = alert.date?.toString() ?: ""
            val single = alert.matches.singleOrNull()
            add {
                AlertRowContent(
                    title = alertText(alert),
                    subtitle = null,
                    dotColor = PitchGreen,
                    trailingIcon = when {
                        single != null -> Res.drawable.ic_chevron_right
                        alert.matches.size > 1 -> if (key in expandedCallUps) Res.drawable.ic_expand_less else Res.drawable.ic_expand_more
                        else -> null
                    },
                    onClick = when {
                        single != null -> { { onNavigateToMatch(single.matchId) } }
                        alert.matches.size > 1 -> { { if (!expandedCallUps.remove(key)) expandedCallUps.add(key) } }
                        else -> null
                    }
                )
            }
            if (alert.matches.size > 1 && key in expandedCallUps) alert.matches.forEach { m ->
                add {
                    Box(Modifier.background(Color(0xFFFAFAFA)).padding(start = 24.dp)) {
                        AlertRowContent(
                            "${m.teamName} vs ${m.opponentName}", null, PitchGreen,
                            Res.drawable.ic_chevron_right, { onNavigateToMatch(m.matchId) }
                        )
                    }
                }
            }
        }
        if (reports.size >= REPORT_GROUP_THRESHOLD) {
            val oldest = reports.mapNotNull { it.date }.minOrNull()
            val anyOverdue = reports.any { reportDotColor(it, today) == Amber }
            add {
                AlertRowContent(
                    title = stringResource(Res.string.msg_home_match_reports_pending, reports.size),
                    subtitle = oldest?.let { stringResource(Res.string.home_reports_oldest, shortDateLabel(it), daysAgoLabel(it, today)) },
                    dotColor = if (anyOverdue) Amber else PitchGreen,
                    trailingIcon = if (expanded) Res.drawable.ic_expand_less else Res.drawable.ic_expand_more,
                    onClick = { expanded = !expanded }
                )
            }
            if (expanded) reports.forEach { alert ->
                add {
                    // Child rows are indented on a faint grey so they read as belonging to the group.
                    Box(Modifier.background(Color(0xFFFAFAFA)).padding(start = 24.dp)) {
                        AlertRowContent(
                            alertText(alert), reportPlayedLine(alert, today), reportDotColor(alert, today),
                            Res.drawable.ic_chevron_right, alert.matchId?.let { id -> { onNavigateToMatch(id) } }
                        )
                    }
                }
            }
        } else {
            reports.forEach { alert ->
                add {
                    AlertRowContent(
                        alertText(alert), reportPlayedLine(alert, today), reportDotColor(alert, today),
                        Res.drawable.ic_chevron_right, alert.matchId?.let { id -> { onNavigateToMatch(id) } }
                    )
                }
            }
        }
        gaps.forEach { alert ->
            add {
                AlertRowContent(
                    alertText(alert), null, DeepCarbon.copy(alpha = 0.25f),
                    Res.drawable.ic_chevron_right, alert.ageGroupId?.let { id -> { onNavigateToAgeGroup(id) } }
                )
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(Res.string.label_home_needs_you))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            color = Color.White,
            border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(CarbonGray.copy(alpha = 0.06f)))
                    row()
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

private const val REPORT_GROUP_THRESHOLD = 3
// A pending report older than this gets an amber dot (27e).
private const val REPORT_OVERDUE_DAYS = 7

private fun reportDotColor(alert: ClubHomeAlert, today: LocalDate): Color =
    if (alert.date != null && alert.date!!.daysUntil(today) > REPORT_OVERDUE_DAYS) Amber else PitchGreen

/** "Played 27 Sep · 3 days ago" -- relative age matters more than the date, so both are shown. */
@Composable
private fun reportPlayedLine(alert: ClubHomeAlert, today: LocalDate): String? {
    val date = alert.date ?: return null
    return stringResource(Res.string.home_report_played, shortDateLabel(date), daysAgoLabel(date, today))
}

@Composable
private fun alertText(alert: ClubHomeAlert): String = when (alert.type) {
    ClubHomeAlertType.AGE_GROUP_NEEDS_COACH ->
        stringResource(Res.string.msg_home_needs_coach, alert.ageGroupName ?: "")
    ClubHomeAlertType.CALLUPS_UNSENT ->
        stringResource(
            Res.string.msg_home_callups_unsent,
            alert.count,
            alert.date?.let { stringResource(weekdayLabel(it.dayOfWeek)) } ?: ""
        )
    ClubHomeAlertType.MATCH_REPORT_PENDING ->
        stringResource(Res.string.msg_home_match_report_pending, alert.teamName ?: "", alert.opponentName ?: "")
}

@Composable
private fun AlertRowContent(
    title: String,
    subtitle: String?,
    dotColor: Color,
    trailingIcon: DrawableResource?,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .align(if (subtitle != null) Alignment.Top else Alignment.CenterVertically)
                .padding(top = if (subtitle != null) 6.dp else 0.dp)
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // The title clamps at two lines; the date line never truncates (27c).
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = DeepCarbon, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.62f))
            }
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(trailingIcon), contentDescription = null, tint = DeepCarbon.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
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
                    ClubHomeAlert(
                        ClubHomeAlertType.CALLUPS_UNSENT, count = 2, date = kotlinx.datetime.LocalDate(2026, 9, 12),
                        matches = listOf(ClubHomeAlertMatch("m1", "Sub-15 A", "AD Fontelas"), ClubHomeAlertMatch("m2", "Sub-13 A", "GD Marinhas"))
                    ),
                    ClubHomeAlert(ClubHomeAlertType.AGE_GROUP_NEEDS_COACH, count = 1, ageGroupId = "ag-x", ageGroupName = "Sub-11"),
                    ClubHomeAlert(
                        ClubHomeAlertType.MATCH_REPORT_PENDING, count = 1, date = kotlinx.datetime.LocalDate(2026, 9, 14),
                        matchId = "m-a", teamName = "Sub-15 C", opponentName = "SC Praia Norte"
                    ),
                    ClubHomeAlert(
                        ClubHomeAlertType.MATCH_REPORT_PENDING, count = 1, date = kotlinx.datetime.LocalDate(2026, 9, 27),
                        matchId = "m-x", teamName = "Sub-15 A", opponentName = "AD Fontelas"
                    )
                )
            ),
            onRetry = {},
            onNavigateToAgeGroup = {},
            onNavigateToMatch = {}
        )
    }
}

@Preview
@Composable
private fun HomeTabManyPendingReportsPreview() {
    val club = Club("c1", "CD Ribamar", status = ClubStatus.ACTIVE, sport = SportType.FOOTBALL, createdAt = Instant.fromEpochMilliseconds(0))
    fun report(id: String, day: Int, team: String, opp: String) = ClubHomeAlert(
        ClubHomeAlertType.MATCH_REPORT_PENDING, count = 1, date = kotlinx.datetime.LocalDate(2026, 9, day),
        matchId = id, teamName = team, opponentName = opp
    )
    SquadifyTheme {
        HomeTab(
            home = ClubHome(
                club = club, seasonName = "26/27", role = ClubRole.MEMBER,
                teamCount = 3, playerCount = 40, coachCount = 3, matchesThisWeek = 2,
                nextUp = emptyList(),
                alerts = listOf(
                    report("m1", 14, "Sub-15 C", "SC Praia Norte"),
                    report("m2", 20, "Sub-15 A", "GD Marinhas"),
                    report("m3", 21, "Sub-15 B", "CD Aveleda"),
                    report("m4", 27, "Sub-15 A", "AD Fontelas")
                )
            ),
            onRetry = {},
            onNavigateToAgeGroup = {},
            onNavigateToMatch = {}
        )
    }
}

