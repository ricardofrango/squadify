package com.ricardofrango.clubmanager.presentation.ui.addplayerstoagegroup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.Player
import com.ricardofrango.clubmanager.model.Season
import com.ricardofrango.clubmanager.model.SeasonType
import com.ricardofrango.clubmanager.presentation.ui.components.FMTopBar
import com.ricardofrango.clubmanager.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.clubmanager.presentation.ui.theme.CarbonGray
import com.ricardofrango.clubmanager.presentation.ui.theme.DeepCarbon
import com.ricardofrango.clubmanager.presentation.ui.theme.ClubManagerTheme
import com.ricardofrango.clubmanager.presentation.ui.theme.PitchGreen
import clubmanager.app.shared.generated.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val FormRoundedShape = RoundedCornerShape(18.dp)

/**
 * Adds club players to an age-group pool. Built to the "Add players to age group" artboard of the
 * design canvas linked in AI_GUIDELINES.md ("UI Design Reference"), consistent with the Create
 * Teams / Create Age Groups screens: an age-group picker (defaults to the age group in the most
 * recently-ending season), a search box, and a scrollable list of **eligible** players -- club
 * players old enough for the group who aren't in it yet (the server returns exactly that set, so
 * there's nothing to filter here beyond search). Tapping a row toggles it; a sticky CREATE adds the
 * whole selection in one request.
 */
@Composable
fun AddPlayersToAgeGroupScreen(
    clubId: String,
    initialAgeGroupId: String? = null,
    onNavigateBack: () -> Unit,
    onPlayersAdded: () -> Unit,
    viewModel: AddPlayersToAgeGroupViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(clubId) {
        viewModel.load(clubId, initialAgeGroupId)
    }

    LaunchedEffect(viewModel.navigationEvent) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                AddPlayersToAgeGroupNavigationEvent.NavigateBackWithPlayersAdded -> onPlayersAdded()
            }
        }
    }

    AddPlayersToAgeGroupContent(
        uiState = uiState,
        searchQuery = searchQuery,
        onNavigateBack = onNavigateBack,
        onSearchChange = { searchQuery = it },
        onAgeGroupSelected = {
            searchQuery = ""
            viewModel.onAgeGroupSelected(it)
        },
        onPlayerToggled = viewModel::onPlayerToggled,
        onAddClick = viewModel::onAddClicked
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlayersToAgeGroupContent(
    uiState: AddPlayersToAgeGroupUiState,
    searchQuery: String,
    onNavigateBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onAgeGroupSelected: (String) -> Unit,
    onPlayerToggled: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val idle = uiState as? AddPlayersToAgeGroupUiState.Idle
    val selectedAgeGroup = idle?.selectedAgeGroup
    val subtitle = selectedAgeGroup?.let { ag ->
        val season = idle.seasonNameFor(ag)
        if (season != null) "${ag.name} · $season" else ag.name
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.title_add_players_to_age_group),
                subtitle = subtitle,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is AddPlayersToAgeGroupUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }

            is AddPlayersToAgeGroupUiState.Idle -> if (uiState.ageGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.msg_no_age_groups_for_add_players),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepCarbon.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val filtered = remember(uiState.eligiblePlayers, searchQuery) {
                    val q = searchQuery.trim().lowercase()
                    if (q.isEmpty()) uiState.eligiblePlayers
                    else uiState.eligiblePlayers.filter {
                        "${it.firstName} ${it.lastName}".lowercase().contains(q)
                    }
                }
                val selectedCount = uiState.selectedPlayerIds.size

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color.White)
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AgeGroupPicker(
                        label = stringResource(Res.string.label_age_group_field),
                        selectedText = selectedAgeGroup?.let { ag ->
                            idle?.seasonNameFor(ag)?.let { "${ag.name} · $it" } ?: ag.name
                        },
                        options = uiState.ageGroups.map { ag ->
                            ag.id to (uiState.seasonNameFor(ag)?.let { "${ag.name} · $it" } ?: ag.name)
                        },
                        onOptionSelected = onAgeGroupSelected
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        label = { Text(stringResource(Res.string.label_search_players)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = FormRoundedShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DeepCarbon,
                            unfocusedTextColor = DeepCarbon,
                            focusedBorderColor = PitchGreen,
                            unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
                            focusedLabelColor = PitchGreen,
                            unfocusedLabelColor = CarbonGray
                        )
                    )

                    if (selectedCount > 0) {
                        Text(
                            text = stringResource(Res.string.label_players_selected, selectedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepCarbon.copy(alpha = 0.6f)
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            uiState.loadingEligible ->
                                CircularProgressIndicator(
                                    color = PitchGreen,
                                    modifier = Modifier.align(Alignment.Center)
                                )

                            filtered.isEmpty() ->
                                EligibleEmptyState(hasSearch = searchQuery.isNotBlank())

                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(filtered, key = { it.id }) { player ->
                                    PlayerRow(
                                        player = player,
                                        selected = player.id in uiState.selectedPlayerIds,
                                        playsUp = selectedAgeGroup != null &&
                                            player.birthDate.year > selectedAgeGroup.birthYear,
                                        onClick = { onPlayerToggled(player.id) }
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.error != null) {
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    val addLabel = if (selectedCount == 1) {
                        stringResource(Res.string.btn_add_player_one)
                    } else {
                        stringResource(Res.string.btn_add_players_other, selectedCount)
                    }
                    PrimaryLoadingButton(
                        text = addLabel,
                        submittingText = stringResource(Res.string.btn_adding_players),
                        isLoading = uiState.isSubmitting,
                        enabled = selectedCount > 0,
                        onClick = onAddClick
                    )
                }
            }
        }
    }
}

@Composable
private fun AgeGroupPicker(
    label: String,
    selectedText: String?,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = DeepCarbon.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            Surface(
                onClick = { if (options.isNotEmpty()) expanded = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = FormRoundedShape,
                color = Color.White,
                border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedText ?: stringResource(Res.string.label_choose_age_group),
                        color = if (selectedText != null) DeepCarbon else DeepCarbon.copy(alpha = 0.4f),
                        fontWeight = if (selectedText != null) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                options.forEach { (value, name) ->
                    DropdownMenuItem(
                        text = { Text(name, color = DeepCarbon) },
                        onClick = {
                            onOptionSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: Player,
    selected: Boolean,
    playsUp: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = FormRoundedShape,
        color = if (selected) PitchGreen.copy(alpha = 0.08f) else Color.White,
        border = BorderStroke(1.dp, if (selected) PitchGreen else CarbonGray.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp)
                    .background(CarbonGray.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${player.firstName.take(1)}${player.lastName.take(1)}",
                    color = DeepCarbon,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${player.firstName} ${player.lastName}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = player.birthDate.year.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeepCarbon.copy(alpha = 0.55f)
                    )
                    if (playsUp) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PitchGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = stringResource(Res.string.label_plays_up),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PitchGreen
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (selected) {
                Box(
                    modifier = Modifier.size(24.dp).background(PitchGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(
                    modifier = Modifier.size(24.dp)
                        .border(1.dp, CarbonGray.copy(alpha = 0.4f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun EligibleEmptyState(hasSearch: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_player),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = DeepCarbon.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (hasSearch) Res.string.msg_no_players_match_search
                else Res.string.msg_no_eligible_players_title
            ),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon,
            textAlign = TextAlign.Center
        )
        if (!hasSearch) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.msg_no_eligible_players_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = DeepCarbon.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---- Previews ----

private fun previewSeason(id: String, name: String) = Season(
    id = id, clubId = "c1", name = name, type = SeasonType.EUROPEAN_YEAR,
    startDate = LocalDate(2026, 7, 1), endDate = LocalDate(2027, 6, 30), active = true
)

private fun previewAgeGroup(id: String, name: String, seasonId: String, birthYear: Int) = AgeGroup(
    id = id, clubId = "c1", name = name, seasonId = seasonId, birthYear = birthYear
)

private fun previewPlayer(id: String, first: String, last: String, birthYear: Int) = Player(
    id = id, clubId = "c1", firstName = first, lastName = last,
    birthDate = LocalDate(birthYear, 3, 14), shirtNumber = null, photoUrl = null, active = true
)

@Preview
@Composable
private fun AddPlayersToAgeGroupContentPreview() {
    ClubManagerTheme {
        AddPlayersToAgeGroupContent(
            uiState = AddPlayersToAgeGroupUiState.Idle(
                ageGroups = listOf(previewAgeGroup("a1", "Sub 2015", "s1", 2015)),
                seasons = listOf(previewSeason("s1", "26/27")),
                selectedAgeGroupId = "a1",
                eligiblePlayers = listOf(
                    previewPlayer("p1", "Afonso", "Rebelo", 2015),
                    previewPlayer("p2", "Bruno", "Costa", 2016),
                    previewPlayer("p3", "Carlos", "Dias", 2015)
                ),
                selectedPlayerIds = setOf("p1")
            ),
            searchQuery = "",
            onNavigateBack = {},
            onSearchChange = {},
            onAgeGroupSelected = {},
            onPlayerToggled = {},
            onAddClick = {}
        )
    }
}

@Preview
@Composable
private fun AddPlayersToAgeGroupEmptyPreview() {
    ClubManagerTheme(darkTheme = true) {
        AddPlayersToAgeGroupContent(
            uiState = AddPlayersToAgeGroupUiState.Idle(
                ageGroups = listOf(previewAgeGroup("a1", "Sub 2015", "s1", 2015)),
                seasons = listOf(previewSeason("s1", "26/27")),
                selectedAgeGroupId = "a1",
                eligiblePlayers = emptyList()
            ),
            searchQuery = "",
            onNavigateBack = {},
            onSearchChange = {},
            onAgeGroupSelected = {},
            onPlayerToggled = {},
            onAddClick = {}
        )
    }
}

@Preview(widthDp = 780, heightDp = 360)
@Composable
private fun AddPlayersToAgeGroupLandscapePreview() {
    ClubManagerTheme {
        AddPlayersToAgeGroupContent(
            uiState = AddPlayersToAgeGroupUiState.Idle(
                ageGroups = listOf(previewAgeGroup("a1", "Sub 2015", "s1", 2015)),
                seasons = listOf(previewSeason("s1", "26/27")),
                selectedAgeGroupId = "a1",
                eligiblePlayers = listOf(previewPlayer("p1", "Afonso", "Rebelo", 2016)),
                selectedPlayerIds = setOf("p1"),
                isSubmitting = true
            ),
            searchQuery = "",
            onNavigateBack = {},
            onSearchChange = {},
            onAgeGroupSelected = {},
            onPlayerToggled = {},
            onAddClick = {}
        )
    }
}
