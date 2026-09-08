package com.ricardofrango.footballmanager.presentation.ui.teamdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.footballmanager.model.*
import com.ricardofrango.footballmanager.presentation.ui.components.FMTopBar
import com.ricardofrango.footballmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.footballmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.footballmanager.presentation.ui.theme.FootballManagerTheme
import com.ricardofrango.footballmanager.presentation.ui.theme.PitchGreen
import footballmanager.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(16.dp)
private val LossRed = Color(0xFFC0392B)

private val DayMonth = LocalDate.Format {
    day(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED)
}

/**
 * The hub for one team. A team is a scheduling handle under an age group, so this screen leads with
 * its fixtures (upcoming + recent results) and links back to the age group for staff and the player
 * pool rather than re-listing them. Built to the "Team details" artboard (12A / 12B) of the design
 * canvas linked in AI_GUIDELINES.md. Manage affordances are gated on [TeamDetails.canManage].
 */
@Composable
fun TeamDetailsScreen(
    teamId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAgeGroup: (ageGroupId: String) -> Unit,
    // Players are reached through the age group now, not from here -- kept for nav-graph compatibility.
    @Suppress("UNUSED_PARAMETER") onNavigateToPlayer: (String) -> Unit,
    onScheduleMatch: () -> Unit,
    onEditTeam: () -> Unit,
    refreshOnReturn: Boolean = false,
    viewModel: TeamDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(teamId) { viewModel.load(teamId) }
    LaunchedEffect(refreshOnReturn) { if (refreshOnReturn) viewModel.load(teamId, forceReload = true) }

    TeamDetailsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.load(teamId, forceReload = true) },
        onNavigateToAgeGroup = onNavigateToAgeGroup,
        onScheduleMatch = onScheduleMatch,
        onEditTeam = onEditTeam
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamDetailsContent(
    uiState: TeamDetailsUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToAgeGroup: (String) -> Unit,
    onScheduleMatch: () -> Unit,
    onEditTeam: () -> Unit
) {
    val details = (uiState as? TeamDetailsUiState.Idle)?.details
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = details?.team?.name ?: stringResource(Res.string.title_team_details),
                subtitle = details?.let { "${it.ageGroup.name} · ${it.season.name}" },
                onNavigateBack = onNavigateBack,
                actions = {
                    if (details != null && details.canManage) {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { open = true }) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_more),
                                    contentDescription = stringResource(Res.string.label_more_options),
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(Color.White)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.label_schedule_match), color = DeepCarbon) },
                                    onClick = { open = false; onScheduleMatch() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.label_edit_team), color = DeepCarbon) },
                                    onClick = { open = false; onEditTeam() }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White)
        when (uiState) {
            is TeamDetailsUiState.Loading ->
                Box(boxModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }

            is TeamDetailsUiState.Error ->
                Column(boxModifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                }

            is TeamDetailsUiState.Idle -> Column(Modifier.fillMaxSize().padding(paddingValues)) {
                val d = uiState.details
                val opponentsById = remember(d.opponents) { d.opponents.associateBy { it.id } }
                TeamHeader(
                    poolSize = d.poolSize,
                    staffCount = d.staff.size,
                    upcoming = d.upcomingMatches.size,
                    played = d.recentResults.size,
                    nextMatch = d.upcomingMatches.firstOrNull(),
                    nextOpponentName = d.upcomingMatches.firstOrNull()
                        ?.let { opponentName(opponentsById, it.opponentId) } ?: ""
                )
                TeamBody(
                    details = d,
                    opponentsById = opponentsById,
                    modifier = Modifier.fillMaxWidth().weight(1f).background(Color.White),
                    onNavigateToAgeGroup = onNavigateToAgeGroup,
                    onScheduleMatch = onScheduleMatch
                )
            }
        }
    }
}

@Composable
private fun TeamBody(
    details: TeamDetails,
    opponentsById: Map<String, Opponent>,
    modifier: Modifier,
    onNavigateToAgeGroup: (String) -> Unit,
    onScheduleMatch: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Upcoming matches ----
        item {
            SectionHeader(
                title = stringResource(Res.string.label_upcoming_matches),
                count = details.upcomingMatches.size,
                onAdd = if (details.canManage) onScheduleMatch else null
            )
        }
        if (details.upcomingMatches.isEmpty()) {
            item {
                EmptySectionCard(
                    title = stringResource(Res.string.msg_no_upcoming_matches),
                    hint = null,
                    action = if (details.canManage) stringResource(Res.string.label_schedule_match) to onScheduleMatch else null
                )
            }
        } else {
            items(details.upcomingMatches, key = { it.id }) { match ->
                UpcomingMatchRow(
                    opponentName = opponentName(opponentsById, match.opponentId),
                    dateTime = match.dateTime,
                    location = match.location
                )
            }
        }

        // ---- Recent results ----
        item {
            SectionHeader(
                title = stringResource(Res.string.label_recent_results),
                count = details.recentResults.size,
                onAdd = null
            )
        }
        if (details.recentResults.isEmpty()) {
            item { EmptySectionCard(title = stringResource(Res.string.msg_no_results_yet), hint = null, action = null) }
        } else {
            item { FormRow(details.recentResults.take(5)) }
            items(details.recentResults.take(5), key = { it.match.id }) { result ->
                ResultRow(
                    opponentName = opponentName(opponentsById, result.match.opponentId),
                    dateTime = result.match.dateTime,
                    ourScore = result.ourScore,
                    opponentScore = result.opponentScore,
                    outcome = result.outcome
                )
            }
        }

        // ---- Staff (read-only; managed on the age group) ----
        item {
            SectionHeader(
                title = stringResource(Res.string.label_age_group_staff),
                count = details.staff.size,
                onAdd = null
            )
        }
        if (details.staff.isEmpty()) {
            item { EmptySectionCard(title = stringResource(Res.string.msg_no_staff_age_group), hint = null, action = null) }
        } else {
            items(details.staff, key = { it.userId }) { member -> StaffRow(member) }
        }
        item {
            AgeGroupLink(
                ageGroupName = details.ageGroup.name,
                onClick = { onNavigateToAgeGroup(details.ageGroup.id) }
            )
        }

        // ---- Player pool ----
        item {
            SectionHeader(title = stringResource(Res.string.label_team_pool_title), count = null, onAdd = null)
        }
        item {
            PoolCard(
                title = stringResource(Res.string.label_players_in_pool, details.poolSize, details.ageGroup.name),
                subtitle = if (details.siblingTeamNames.isNotEmpty()) {
                    stringResource(Res.string.msg_pool_shared_with, details.siblingTeamNames.joinToString(", "))
                } else {
                    stringResource(Res.string.msg_pool_no_fixed_squad)
                },
                onClick = { onNavigateToAgeGroup(details.ageGroup.id) }
            )
        }
    }
}

// ---- Header ----

/** Dark block directly under the top bar (same CarbonGray): the next fixture + edge-to-edge counts. */
@Composable
private fun TeamHeader(
    poolSize: Int,
    staffCount: Int,
    upcoming: Int,
    played: Int,
    nextMatch: Match?,
    nextOpponentName: String
) {
    Column(modifier = Modifier.fillMaxWidth().background(CarbonGray)) {
        if (nextMatch != null) {
            NextMatchCard(
                opponentName = nextOpponentName,
                dateTime = nextMatch.dateTime,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Stat(poolSize.toString(), stringResource(Res.string.label_pool), Modifier.weight(1f))
            HeaderDivider()
            Stat(staffCount.toString(), stringResource(Res.string.label_age_group_staff), Modifier.weight(1f))
            HeaderDivider()
            Stat(upcoming.toString(), stringResource(Res.string.label_upcoming), Modifier.weight(1f), highlight = upcoming > 0)
            HeaderDivider()
            Stat(played.toString(), stringResource(Res.string.label_played), Modifier.weight(1f))
        }
    }
}

@Composable
private fun NextMatchCard(opponentName: String, dateTime: Instant, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.08f), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateBlock(dateTime, onDark = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "vs $opponentName",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${timeLabel(dateTime)} · ${relativeLabel(dateTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Surface(shape = RoundedCornerShape(6.dp), color = PitchGreen.copy(alpha = 0.20f)) {
                Text(
                    stringResource(Res.string.label_next),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PitchGreen
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
    Column(modifier = modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = if (highlight) PitchGreen else Color.White
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun HeaderDivider() {
    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.10f)))
}

// ---- Rows ----

@Composable
private fun DateBlock(dateTime: Instant, onDark: Boolean) {
    val dt = dateTime.toLocalDateTime(TimeZone.currentSystemDefault())
    val weekday = dt.dayOfWeek.name.take(3).uppercase()
    val primary = if (onDark) Color.White else DeepCarbon
    val secondary = if (onDark) Color.White.copy(alpha = 0.5f) else DeepCarbon.copy(alpha = 0.5f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
        Text(weekday, style = MaterialTheme.typography.labelSmall, color = secondary)
        Text(
            dt.day.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = primary
        )
    }
}

@Composable
private fun UpcomingMatchRow(opponentName: String, dateTime: Instant, location: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DateBlock(dateTime, onDark = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "vs $opponentName",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(timeLabel(dateTime), location?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    opponentName: String,
    dateTime: Instant,
    ourScore: Int,
    opponentScore: Int,
    outcome: MatchOutcome
) {
    val accent = outcomeColor(outcome)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "vs $opponentName",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).date.format(DayMonth),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.55f)
                    )
                }
                Text(
                    "$ourScore–$opponentScore",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun FormRow(results: List<TeamMatchResult>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(Res.string.label_form),
            style = MaterialTheme.typography.bodySmall,
            color = DeepCarbon.copy(alpha = 0.5f)
        )
        Spacer(Modifier.width(2.dp))
        results.forEach { r ->
            Box(
                modifier = Modifier.size(22.dp).background(outcomeColor(r.outcome), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    outcomeLetter(r.outcome),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun StaffRow(member: AgeGroupStaffMember) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar("${member.firstName.take(1)}${member.lastName.take(1)}")
            Spacer(Modifier.width(12.dp))
            Text(
                "${member.firstName} ${member.lastName}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            MiniChip(
                stringResource(if (member.role == TeamRole.COACH) Res.string.label_role_coach else Res.string.label_role_staff)
            )
        }
    }
}

/**
 * Footnote: "...managed at the age-group level — {name}", where {name} is a green, bold, tappable
 * link. One [Text] with an [androidx.compose.ui.text.AnnotatedString] so it wraps as a single
 * paragraph -- rendering the name in a sibling [Text] pushes it to the edge of the row.
 */
@Composable
private fun AgeGroupLink(ageGroupName: String, onClick: () -> Unit) {
    val full = stringResource(Res.string.msg_staff_managed_at_age_group, ageGroupName)
    // The green/bold has to ride on the LinkAnnotation via TextLinkStyles -- a plain SpanStyle over
    // the same range is ignored once the range is also a link, so the name rendered grey.
    val linkStyle = TextLinkStyles(style = SpanStyle(color = PitchGreen, fontWeight = FontWeight.Bold))
    val annotated = buildAnnotatedString {
        append(full)
        val start = full.lastIndexOf(ageGroupName)
        if (start >= 0) {
            addLink(LinkAnnotation.Clickable(tag = "ageGroup", styles = linkStyle) { onClick() }, start, start + ageGroupName.length)
        }
    }
    Text(
        annotated,
        style = MaterialTheme.typography.bodySmall,
        color = DeepCarbon.copy(alpha = 0.5f)
    )
}

@Composable
private fun PoolCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(PitchGreen.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_age_group),
                    contentDescription = null,
                    tint = PitchGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.6f))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(Res.string.label_view_pool),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PitchGreen
            )
        }
    }
}

// ---- Shared bits (kept local, mirroring AgeGroupDetailsScreen) ----

@Composable
private fun SectionHeader(title: String, count: Int?, onAdd: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.4f))
        }
        Spacer(Modifier.weight(1f))
        if (onAdd != null) {
            Text(
                "+ ${stringResource(Res.string.label_add)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PitchGreen,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAdd
                )
            )
        }
    }
}

@Composable
private fun EmptySectionCard(title: String, hint: String?, action: Pair<String, () -> Unit>?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, textAlign = TextAlign.Center)
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            }
            if (action != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = action.second,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)
                ) { Text(action.first) }
            }
        }
    }
}

@Composable
private fun InitialsAvatar(initials: String) {
    Box(
        modifier = Modifier.size(40.dp).background(CarbonGray.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Text(initials, color = DeepCarbon, fontWeight = FontWeight.Bold) }
}

@Composable
private fun MiniChip(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = PitchGreen.copy(alpha = 0.15f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = PitchGreen
        )
    }
}

// ---- Helpers ----

private fun opponentName(opponentsById: Map<String, Opponent>, opponentId: String): String =
    opponentsById[opponentId]?.let { it.shortName ?: it.name } ?: "—"

private fun outcomeColor(outcome: MatchOutcome): Color = when (outcome) {
    MatchOutcome.WIN -> PitchGreen
    MatchOutcome.DRAW -> CarbonGray.copy(alpha = 0.45f)
    MatchOutcome.LOSS -> LossRed
}

private fun outcomeLetter(outcome: MatchOutcome): String = when (outcome) {
    MatchOutcome.WIN -> "W"
    MatchOutcome.DRAW -> "D"
    MatchOutcome.LOSS -> "L"
}

private fun timeLabel(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

@Composable
private fun relativeLabel(instant: Instant): String {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val target = instant.toLocalDateTime(tz).date
    val days = (target.toEpochDays() - today.toEpochDays()).toInt()
    return when {
        days <= 0 -> stringResource(Res.string.label_relative_today)
        days == 1 -> stringResource(Res.string.label_relative_tomorrow)
        days < 7 -> stringResource(Res.string.label_relative_in_days, days)
        else -> stringResource(Res.string.label_relative_in_weeks, days / 7)
    }
}

// ---- Previews ----

private fun sampleDetails(populated: Boolean, canManage: Boolean): TeamDetails {
    val ag = AgeGroup(id = "ag1", clubId = "c1", name = "Sub-15", seasonId = "s1", birthYear = 2011)
    val season = Season(id = "s1", clubId = "c1", name = "26/27", type = SeasonType.EUROPEAN_YEAR, startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2027, 6, 30), active = true)
    val team = Team(id = "t1", clubId = "c1", ageGroupId = "ag1", seasonId = "s1", name = "Sub-15 A", code = "S15A", active = true)
    val opponents = listOf(
        Opponent(id = "o1", clubId = "c1", name = "AD Fontelas", shortName = "AD Fontelas"),
        Opponent(id = "o2", clubId = "c1", name = "CD Aveleda", shortName = "CD Aveleda")
    )
    val base = Instant.fromEpochMilliseconds(1_760_000_000_000)
    fun match(id: String, oppId: String, status: MatchStatus, location: String?) =
        Match(id = id, clubId = "c1", teamId = "t1", opponentId = oppId, location = location, dateTime = base, status = status)
    return TeamDetails(
        team = team,
        ageGroup = ag,
        season = season,
        poolSize = 14,
        siblingTeamNames = if (populated) listOf("Sub-15 B") else emptyList(),
        staff = if (populated) listOf(
            AgeGroupStaffMember("u1", "João", "Camelo", TeamRole.COACH),
            AgeGroupStaffMember("u2", "Rita", "Sá", TeamRole.COACH),
            AgeGroupStaffMember("u3", "Paulo", "Mesquita", TeamRole.STAFF)
        ) else listOf(AgeGroupStaffMember("u1", "Mário", "Aleixo", TeamRole.COACH)),
        upcomingMatches = if (populated) listOf(
            match("m1", "o1", MatchStatus.SCHEDULED, "Campo do Ribamar"),
            match("m2", "o2", MatchStatus.SCHEDULED, "Away")
        ) else emptyList(),
        recentResults = if (populated) listOf(
            TeamMatchResult(match("r1", "o1", MatchStatus.FINISHED, null), 3, 1, MatchOutcome.WIN),
            TeamMatchResult(match("r2", "o2", MatchStatus.FINISHED, null), 2, 0, MatchOutcome.WIN),
            TeamMatchResult(match("r3", "o1", MatchStatus.FINISHED, null), 1, 1, MatchOutcome.DRAW),
            TeamMatchResult(match("r4", "o2", MatchStatus.FINISHED, null), 0, 2, MatchOutcome.LOSS)
        ) else emptyList(),
        opponents = opponents,
        canManage = canManage
    )
}

@Preview
@Composable
private fun TeamDetailsPopulatedPreview() {
    FootballManagerTheme {
        TeamDetailsContent(
            uiState = TeamDetailsUiState.Idle(sampleDetails(populated = true, canManage = true)),
            onNavigateBack = {}, onRetry = {}, onNavigateToAgeGroup = {}, onScheduleMatch = {}, onEditTeam = {}
        )
    }
}

@Preview
@Composable
private fun TeamDetailsEmptyPreview() {
    FootballManagerTheme(darkTheme = true) {
        TeamDetailsContent(
            uiState = TeamDetailsUiState.Idle(sampleDetails(populated = false, canManage = true)),
            onNavigateBack = {}, onRetry = {}, onNavigateToAgeGroup = {}, onScheduleMatch = {}, onEditTeam = {}
        )
    }
}
