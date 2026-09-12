package com.ricardofrango.clubmanager.presentation.ui.playerdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.clubmanager.domain.AgeGroupDefaults
import com.ricardofrango.clubmanager.model.EventType
import com.ricardofrango.clubmanager.model.Player
import com.ricardofrango.clubmanager.model.PlayerProfile
import com.ricardofrango.clubmanager.model.PlayerProfileEvent
import com.ricardofrango.clubmanager.presentation.ui.components.FMTopBar
import com.ricardofrango.clubmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.clubmanager.presentation.ui.theme.ClubManagerTheme
import com.ricardofrango.clubmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.clubmanager.presentation.ui.theme.PitchGreen
import clubmanager.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(16.dp)
private val CardYellow = Color(0xFFF1C40F)
private val LiveRed = Color(0xFFC0392B)

private val DayMonthYear = LocalDate.Format { day(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED); char(' '); year() }
private val DayMonth = LocalDate.Format { day(); char(' '); monthName(MonthNames.ENGLISH_ABBREVIATED) }

/**
 * The player profile screen (design canvas artboard "2C", see AI_GUIDELINES.md "UI Design
 * Reference"). One dark header (avatar, name, shirt number/position/age-group line) over a white
 * body: a fixed stat row (goals/assists/times called up -- all-time, no season/last-5/career split
 * yet), a Player Information card, and a Recent Events list. Read-only; there's no edit affordance on
 * this screen yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailsScreen(
    playerId: String,
    onNavigateBack: () -> Unit,
    viewModel: PlayerDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(playerId) {
        viewModel.loadPlayerDetails(playerId)
    }

    PlayerDetailsContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.loadPlayerDetails(playerId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerDetailsContent(
    uiState: PlayerDetailsUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit
) {
    val profile = (uiState as? PlayerDetailsUiState.Idle)?.profile

    Scaffold(
        containerColor = Color.White,
        topBar = {
            Column(Modifier.background(CarbonGray)) {
                FMTopBar(
                    title = stringResource(Res.string.title_player_profile),
                    onNavigateBack = onNavigateBack
                )
                if (profile != null) PlayerHeaderBand(profile)
            }
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White)
        when (uiState) {
            is PlayerDetailsUiState.Loading -> {
                Box(boxModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }
            is PlayerDetailsUiState.Error ->
                Column(boxModifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                }
            is PlayerDetailsUiState.Idle -> PlayerBody(uiState.profile, modifier = boxModifier)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Header (dark)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlayerHeaderBand(profile: PlayerProfile) {
    val player = profile.player
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = PitchGreen.copy(alpha = 0.25f)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "${player.firstName.take(1)}${player.lastName.take(1)}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = PitchGreen
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                "${player.firstName} ${player.lastName}",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                player.shirtNumber?.let { number ->
                    Surface(shape = RoundedCornerShape(6.dp), color = PitchGreen) {
                        Text(
                            number.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    subtitleLine(profile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/** "Midfielder · Sub 11" (position omitted if not set -- there's no way to set one yet). */
private fun subtitleLine(profile: PlayerProfile): String {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val subLabel = AgeGroupDefaults.inferredName(
        birthYear = profile.player.birthDate.year,
        seasonEndYear = AgeGroupDefaults.currentSeasonEndYear(today)
    )
    return listOfNotNull(profile.player.position?.takeIf { it.isNotBlank() }, subLabel).joinToString(" · ")
}

// ---------------------------------------------------------------------------------------------
// Body (white)
// ---------------------------------------------------------------------------------------------

private const val MAX_RECENT_EVENTS_SHOWN = 5

@Composable
private fun PlayerBody(profile: PlayerProfile, modifier: Modifier = Modifier) {
    val player = profile.player
    val recentEvents = remember(profile.recentEvents) { profile.recentEvents.take(MAX_RECENT_EVENTS_SHOWN) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(Res.string.label_stat_goals), profile.goals, Modifier.weight(1f), accent = true)
                StatCard(stringResource(Res.string.label_stat_assists), profile.assists, Modifier.weight(1f))
                StatCard(stringResource(Res.string.label_stat_called_up), profile.timesCalledUp, Modifier.weight(1f))
            }
        }

        item {
            Surface(shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(Res.string.label_player_info),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = DeepCarbon
                    )
                    Spacer(Modifier.height(12.dp))
                    InfoRow(
                        stringResource(Res.string.label_status),
                        stringResource(if (player.active) Res.string.label_active else Res.string.label_inactive)
                    )
                    InfoRow(stringResource(Res.string.label_birth_date), player.birthDate.format(DayMonthYear))
                    InfoRow(stringResource(Res.string.label_age_group_field), profile.ageGroupLabel ?: "—")
                    InfoRow(stringResource(Res.string.label_guardians), stringResource(Res.string.label_guardians_linked, profile.guardiansLinked))
                }
            }
        }

        item {
            Text(
                stringResource(Res.string.label_recent_events),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon
            )
        }

        if (recentEvents.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.msg_no_recent_events),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeepCarbon.copy(alpha = 0.5f)
                )
            }
        } else {
            items(recentEvents) { EventRow(it) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier, accent: Boolean = false) {
    Surface(modifier = modifier, shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (accent) PitchGreen else DeepCarbon
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
    }
}

@Composable
private fun EventRow(event: PlayerProfileEvent) {
    val (glyph, glyphColor) = glyphFor(event.type)
    Surface(modifier = Modifier.fillMaxWidth(), shape = CardShape, color = Color.White, border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.12f))) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(glyphColor.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Text(glyph, color = glyphColor, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${stringResource(labelFor(event.type))} · ${event.minute}'",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon
                )
                Text(
                    "v ${event.opponentName} · ${matchDateLabel(event.matchDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.55f)
                )
            }
        }
    }
}

private fun glyphFor(type: EventType): Pair<String, Color> = when (type) {
    EventType.GOAL -> "G" to PitchGreen
    EventType.OWN_GOAL -> "OG" to CarbonGray
    EventType.ASSIST -> "A" to PitchGreen
    EventType.YELLOW_CARD -> "Y" to CardYellow
    EventType.RED_CARD -> "R" to LiveRed
    EventType.SUBSTITUTION_IN -> "⇄" to PitchGreen
    EventType.SUBSTITUTION_OUT -> "⇄" to CarbonGray
}

private fun labelFor(type: EventType) = when (type) {
    EventType.GOAL -> Res.string.label_event_type_goal
    EventType.OWN_GOAL -> Res.string.label_event_type_own_goal
    EventType.ASSIST -> Res.string.label_event_type_assist
    EventType.YELLOW_CARD -> Res.string.label_event_type_yellow_card
    EventType.RED_CARD -> Res.string.label_event_type_red_card
    EventType.SUBSTITUTION_IN -> Res.string.label_event_type_substitution_in
    EventType.SUBSTITUTION_OUT -> Res.string.label_event_type_substitution_out
}

private fun matchDateLabel(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.format(DayMonth)

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun sampleProfile(withPosition: Boolean, withEvents: Boolean): PlayerProfile = PlayerProfile(
    player = Player(
        id = "p1", clubId = "c1", firstName = "Tomás", lastName = "Bragança",
        birthDate = LocalDate(2011, 3, 12), shirtNumber = 10, active = true,
        position = if (withPosition) "Midfielder" else null
    ),
    ageGroupLabel = "Sub-15 A",
    goals = 9,
    assists = 6,
    timesCalledUp = 14,
    guardiansLinked = 2,
    recentEvents = if (withEvents) {
        listOf(
            PlayerProfileEvent(EventType.GOAL, 63, "AD Fontelas", Instant.fromEpochMilliseconds(0)),
            PlayerProfileEvent(EventType.ASSIST, 71, "AD Fontelas", Instant.fromEpochMilliseconds(0)),
            PlayerProfileEvent(EventType.YELLOW_CARD, 44, "GD Marinhas", Instant.fromEpochMilliseconds(0))
        )
    } else {
        emptyList()
    }
)

@Preview
@Composable
private fun PlayerDetailsPopulatedPreview() {
    ClubManagerTheme {
        PlayerDetailsContent(
            uiState = PlayerDetailsUiState.Idle(sampleProfile(withPosition = true, withEvents = true)),
            onNavigateBack = {},
            onRetry = {}
        )
    }
}

@Preview
@Composable
private fun PlayerDetailsNoPositionNoEventsPreview() {
    ClubManagerTheme {
        PlayerDetailsContent(
            uiState = PlayerDetailsUiState.Idle(sampleProfile(withPosition = false, withEvents = false)),
            onNavigateBack = {},
            onRetry = {}
        )
    }
}

@Preview
@Composable
private fun PlayerDetailsLoadingPreview() {
    ClubManagerTheme {
        PlayerDetailsContent(uiState = PlayerDetailsUiState.Loading, onNavigateBack = {}, onRetry = {})
    }
}

@Preview
@Composable
private fun PlayerDetailsErrorPreview() {
    ClubManagerTheme {
        PlayerDetailsContent(
            uiState = PlayerDetailsUiState.Error("Couldn't load this player. Please try again."),
            onNavigateBack = {},
            onRetry = {}
        )
    }
}
