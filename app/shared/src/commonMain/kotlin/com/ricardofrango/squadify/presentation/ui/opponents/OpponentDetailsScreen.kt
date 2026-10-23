package com.ricardofrango.squadify.presentation.ui.opponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.Match
import com.ricardofrango.squadify.model.MatchScore
import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.MatchType
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.OpponentDetails
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.MatchRow
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsHeaderRow
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsMessage
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.teamdetails.FormSquare
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SuccessGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_directions
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.cd_edit_opponent
import squadify.app.shared.generated.resources.ic_edit
import squadify.app.shared.generated.resources.h2h_goals
import squadify.app.shared.generated.resources.h2h_last_5
import squadify.app.shared.generated.resources.h2h_no_results_season
import squadify.app.shared.generated.resources.h2h_oldest_first
import squadify.app.shared.generated.resources.h2h_played
import squadify.app.shared.generated.resources.label_contact_section
import squadify.app.shared.generated.resources.label_head_to_head
import squadify.app.shared.generated.resources.label_matches_count
import squadify.app.shared.generated.resources.label_opponent_notes_field
import squadify.app.shared.generated.resources.label_opponent_stadium_section
import squadify.app.shared.generated.resources.msg_check_connection
import squadify.app.shared.generated.resources.msg_h2h_after_first_match
import squadify.app.shared.generated.resources.msg_no_address
import squadify.app.shared.generated.resources.msg_no_matches_against
import squadify.app.shared.generated.resources.msg_opponent_load_failed
import squadify.app.shared.generated.resources.opponent_matches_all_time
import squadify.app.shared.generated.resources.record_drawn
import squadify.app.shared.generated.resources.record_lost
import squadify.app.shared.generated.resources.record_won
import squadify.app.shared.generated.resources.title_opponent
import kotlin.time.Instant

/**
 * Opponent details (design 38f/g), opened from the Opponents list: the header (logo, name, short name,
 * all-time matches), the stadium with Directions, the head-to-head for one season (Played · W · D · L,
 * goals, the last five oldest first) with a season picker, every match against them (tap to open), then
 * contact and notes when filled. Two columns on a wide screen. The pencil (Edit opponent) shows only
 * with permission.
 */
@Composable
fun OpponentDetailsScreen(
    opponentId: String,
    onNavigateBack: () -> Unit,
    onOpenMatch: (matchId: String) -> Unit,
    onEdit: () -> Unit,
    // True right after returning from Edit opponent: reload.
    refresh: Boolean = false,
    viewModel: OpponentDetailsViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val state = viewModel.state.collectSettledState()
        LaunchedEffect(opponentId) { viewModel.load(opponentId) }
        LaunchedEffect(refresh) { if (refresh) viewModel.load(opponentId, force = true) }

        OpponentDetailsContent(
            state = state,
            onNavigateBack = onNavigateBack,
            onEdit = onEdit,
            onRetry = { viewModel.load(opponentId, force = true) },
            onSeasonSelected = viewModel::onSeasonSelected,
            onOpenMatch = onOpenMatch
        )
    }
}

@Composable
private fun OpponentDetailsContent(
    state: OpponentDetailsUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onSeasonSelected: (String) -> Unit,
    onOpenMatch: (String) -> Unit,
    onEdit: () -> Unit = {}
) {
    val details = (state as? OpponentDetailsUiState.Idle)?.details
    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = details?.opponent?.name ?: stringResource(Res.string.title_opponent),
                subtitle = details?.clubName,
                onNavigateBack = onNavigateBack,
                actions = {
                    // Only with EDIT_OPPONENT; no disabled version (design 38i).
                    if (details?.canEdit == true) {
                        IconButton(onClick = onEdit) {
                            Icon(painterResource(Res.drawable.ic_edit), stringResource(Res.string.cd_edit_opponent), tint = Color.White)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF6F6F7))
        when (state) {
            OpponentDetailsUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PitchGreen) }
            OpponentDetailsUiState.Error -> Column(
                modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(Res.string.msg_opponent_load_failed), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.msg_check_connection), color = DeepCarbon.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = PitchGreen, contentColor = Color.White)) { Text(stringResource(Res.string.btn_retry)) }
            }
            is OpponentDetailsUiState.Idle -> BoxWithConstraints(modifier) {
                val d = state.details
                if (maxWidth >= 600.dp) {
                    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HeaderCard(d)
                            StadiumCard(d.opponent)
                            ContactCard(d.opponent)
                            NotesCard(d.opponent)
                        }
                        Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HeadToHeadCard(d, state.seasonId, onSeasonSelected, onOpenMatch)
                            MatchesSection(d, onOpenMatch)
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeaderCard(d)
                        StadiumCard(d.opponent)
                        HeadToHeadCard(d, state.seasonId, onSeasonSelected, onOpenMatch)
                        MatchesSection(d, onOpenMatch)
                        ContactCard(d.opponent)
                        NotesCard(d.opponent)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(details: OpponentDetails) {
    val opponent = details.opponent
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OpponentLogo(opponent.name, opponent.shortName, opponent.logoUrl, size = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    opponent.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    opponent.shortName?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = DeepCarbon.copy(alpha = 0.7f),
                            modifier = Modifier.background(CarbonGray.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    val count = allTimeMatchCount(details)
                    Text(pluralStringResource(Res.plurals.opponent_matches_all_time, count, count), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
                }
            }
        }
    }
}

@Composable
private fun StadiumCard(opponent: Opponent) {
    if (opponent.stadiumName == null && opponent.stadiumAddress == null) return
    val uriHandler = LocalUriHandler.current
    StatsCard {
        StatsHeading(stringResource(Res.string.label_opponent_stadium_section), Modifier.padding(bottom = 8.dp))
        opponent.stadiumName?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        }
        Text(
            opponent.stadiumAddress ?: stringResource(Res.string.msg_no_address),
            style = MaterialTheme.typography.bodyMedium,
            color = StatsSecondaryText
        )
        opponent.stadiumAddress?.let { address ->
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { uriHandler.openUri(mapsUrl(address)) }, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(Res.string.btn_directions), color = PitchGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeadToHeadCard(details: OpponentDetails, seasonId: String?, onSeasonSelected: (String) -> Unit, onOpenMatch: (String) -> Unit) {
    val title = stringResource(Res.string.label_head_to_head)
    StatsCard {
        if (!hasFinishedMatches(details)) {
            StatsHeading(title, Modifier.padding(bottom = 10.dp))
            StatsMessage(
                stringResource(Res.string.msg_no_matches_against, details.opponent.name),
                stringResource(Res.string.msg_h2h_after_first_match)
            )
            return@StatsCard
        }
        val seasons = headToHeadSeasons(details, seasonId)
        val season = seasons.firstOrNull { it.id == seasonId }
        var pickerOpen by remember { mutableStateOf(false) }
        Box {
            StatsHeaderRow(title, season?.name, onPillClick = if (seasons.size > 1) ({ pickerOpen = true }) else null)
            DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }, modifier = Modifier.background(Color.White)) {
                seasons.forEach { s ->
                    DropdownMenuItem(text = { Text(s.name, color = DeepCarbon) }, onClick = { pickerOpen = false; onSeasonSelected(s.id) })
                }
            }
        }
        val t = headToHead(details, seasonId)
        if (t.played == 0) {
            Text(stringResource(Res.string.h2h_no_results_season, season?.name.orEmpty()), style = MaterialTheme.typography.bodyMedium, color = StatsSecondaryText)
            return@StatsCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(t.played, stringResource(Res.string.h2h_played), DeepCarbon, Modifier.weight(1f))
            Tile(t.won, stringResource(Res.string.record_won), SuccessGreen, Modifier.weight(1f))
            Tile(t.drawn, stringResource(Res.string.record_drawn), DeepCarbon, Modifier.weight(1f))
            Tile(t.lost, stringResource(Res.string.record_lost), AlertRed, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                buildAnnotatedString {
                    append("${stringResource(Res.string.h2h_goals)} ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append("${t.goalsFor} – ${t.goalsAgainst}") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = DeepCarbon
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.h2h_last_5), style = MaterialTheme.typography.labelLarge, color = StatsSecondaryText)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                // Oldest on the left (design 38i).
                t.form.reversed().forEach { FormSquare(it) { onOpenMatch(it.matchId) } }
            }
            Text(stringResource(Res.string.h2h_oldest_first), style = MaterialTheme.typography.labelMedium, color = StatsSecondaryText)
        }
    }
}

@Composable
private fun Tile(value: Int, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, fontSize = 24.sp), color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = StatsSecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MatchesSection(details: OpponentDetails, onOpenMatch: (String) -> Unit) {
    if (details.matches.isEmpty()) return
    val teams = details.teams.associateBy { it.id }
    val scores = details.scores.associateBy { it.matchId }
    val opponentName = details.opponent.shortName ?: details.opponent.name
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsHeading(stringResource(Res.string.label_matches_count, details.matches.size), Modifier.padding(start = 4.dp, top = 4.dp))
        details.matches.forEach { match ->
            MatchRow(
                match = match,
                opponentName = opponentName,
                teamName = teams[match.teamId]?.name,
                score = scores[match.id],
                onClick = { onOpenMatch(match.id) }
            )
        }
    }
}

@Composable
private fun ContactCard(opponent: Opponent) {
    if (opponent.contactEmail == null && opponent.contactPhone == null) return
    val uriHandler = LocalUriHandler.current
    StatsCard {
        StatsHeading(stringResource(Res.string.label_contact_section), Modifier.padding(bottom = 6.dp))
        opponent.contactEmail?.let { email -> ContactRow(email) { uriHandler.openUri("mailto:$email") } }
        opponent.contactPhone?.let { phone -> ContactRow(phone) { uriHandler.openUri("tel:${phone.filter { it.isDigit() || it == '+' }}") } }
    }
}

@Composable
private fun ContactRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = PitchGreen,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)
    )
}

@Composable
private fun NotesCard(opponent: Opponent) {
    val notes = opponent.notes ?: return
    StatsCard {
        StatsHeading(stringResource(Res.string.label_opponent_notes_field), Modifier.padding(bottom = 6.dp))
        Text(notes, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon)
    }
}

// ---- Previews ----

private val previewDetails = OpponentDetails(
    opponent = Opponent(
        "o1", "c1", "AD Fontelas", "ADF",
        stadiumName = "Campo das Fontelas", stadiumAddress = "Rua do Campo 3, 2640-401 Mafra",
        contactEmail = "geral@adfontelas.pt", contactPhone = "+351 261 812 440",
        notes = "Artificial pitch, parking behind the stand."
    ),
    clubName = "CD Ribamar",
    matches = listOf(
        Match("m3", "c1", "t2", "o1", dateTime = Instant.parse("2026-10-18T09:30:00Z"), status = MatchStatus.SCHEDULED, type = MatchType.CHAMPIONSHIP),
        Match("m2", "c1", "t1", "o1", dateTime = Instant.parse("2026-09-27T09:30:00Z"), status = MatchStatus.FINISHED, type = MatchType.CHAMPIONSHIP),
        Match("m1", "c1", "t1", "o1", dateTime = Instant.parse("2026-09-14T09:30:00Z"), status = MatchStatus.FINISHED, type = MatchType.FRIENDLY)
    ),
    scores = listOf(MatchScore("m2", 3, 1), MatchScore("m1", 0, 2)),
    teams = listOf(Team("t1", "c1", "ag1", "s1", "Sub-15 A", active = true), Team("t2", "c1", "ag2", "s1", "Sub-13 A", active = true)),
    seasons = listOf(Season("s1", "c1", "2026/27", SeasonType.EUROPEAN_YEAR, LocalDate(2026, 9, 1), LocalDate(2027, 6, 30), true)),
    canEdit = true
)

@Preview
@Composable
private fun OpponentDetailsPreview() {
    SquadifyTheme { OpponentDetailsContent(OpponentDetailsUiState.Idle(previewDetails, "s1"), {}, {}, {}, {}) }
}

@Preview
@Composable
private fun OpponentDetailsNoMatchesPreview() {
    SquadifyTheme {
        OpponentDetailsContent(
            OpponentDetailsUiState.Idle(
                previewDetails.copy(
                    opponent = Opponent("o2", "c1", "UD Santo Isidoro", "UDSI", stadiumName = "Campo de Santo Isidoro"),
                    matches = emptyList(), scores = emptyList(), teams = emptyList()
                ),
                "s1"
            ),
            {}, {}, {}, {}
        )
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun OpponentDetailsLandscapePreview() {
    SquadifyTheme { OpponentDetailsContent(OpponentDetailsUiState.Idle(previewDetails, "s1"), {}, {}, {}, {}) }
}

@Preview
@Composable
private fun OpponentDetailsErrorPreview() {
    SquadifyTheme { OpponentDetailsContent(OpponentDetailsUiState.Error, {}, {}, {}, {}) }
}
