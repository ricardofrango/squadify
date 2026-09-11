package com.ricardofrango.clubmanager.presentation.ui.addstafftoagegroup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ricardofrango.clubmanager.model.ClubMemberSummary
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.Season
import com.ricardofrango.clubmanager.model.SeasonType
import com.ricardofrango.clubmanager.model.TeamRole
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
 * Assigns club members as an age group's coaches/staff. Same shape as AddPlayersToAgeGroupScreen
 * (age-group picker, search, scrollable eligible list), except each row picks a [TeamRole] rather
 * than being a plain toggle -- staff must already be a club member (`AddStaffToAgeGroupUseCase`
 * requires it), so the eligible list is club members not already staff of the selected age group.
 */
@Composable
fun AddStaffToAgeGroupScreen(
    clubId: String,
    clubName: String,
    initialAgeGroupId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToCreateStaff: () -> Unit,
    onStaffAdded: () -> Unit,
    // True once, on return from the create-staff screen -- reload the eligible list so the
    // just-registered members are pickable straight away.
    refreshOnReturn: Boolean = false,
    viewModel: AddStaffToAgeGroupViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(clubId) {
        viewModel.load(clubId, initialAgeGroupId)
    }

    LaunchedEffect(refreshOnReturn) {
        if (refreshOnReturn) viewModel.refreshEligibleMembers()
    }

    LaunchedEffect(viewModel.navigationEvent) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                AddStaffToAgeGroupNavigationEvent.NavigateBackWithStaffAdded -> onStaffAdded()
            }
        }
    }

    AddStaffToAgeGroupContent(
        uiState = uiState,
        searchQuery = searchQuery,
        onNavigateBack = onNavigateBack,
        onSearchChange = { searchQuery = it },
        onAgeGroupSelected = {
            searchQuery = ""
            viewModel.onAgeGroupSelected(clubId, it)
        },
        onRoleTapped = viewModel::onRoleTapped,
        onAddNewStaff = onNavigateToCreateStaff,
        onAddClick = viewModel::onAddClicked
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStaffToAgeGroupContent(
    uiState: AddStaffToAgeGroupUiState,
    searchQuery: String,
    onNavigateBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onAgeGroupSelected: (String) -> Unit,
    onRoleTapped: (userId: String, role: TeamRole) -> Unit,
    onAddNewStaff: () -> Unit,
    onAddClick: () -> Unit
) {
    val idle = uiState as? AddStaffToAgeGroupUiState.Idle
    val selectedAgeGroup = idle?.selectedAgeGroup
    val subtitle = selectedAgeGroup?.let { ag ->
        val season = idle.seasonNameFor(ag)
        if (season != null) "${ag.name} · $season" else ag.name
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            FMTopBar(
                title = stringResource(Res.string.title_add_staff_to_age_group),
                subtitle = subtitle,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is AddStaffToAgeGroupUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }

            is AddStaffToAgeGroupUiState.Idle -> if (uiState.ageGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.msg_no_age_groups_for_add_staff),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepCarbon.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val filtered = remember(uiState.eligibleMembers, searchQuery) {
                    val q = searchQuery.trim().lowercase()
                    if (q.isEmpty()) uiState.eligibleMembers
                    else uiState.eligibleMembers.filter {
                        "${it.firstName} ${it.lastName}".lowercase().contains(q)
                    }
                }
                val selectedCount = uiState.selectedRoles.size

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
                        label = { Text(stringResource(Res.string.label_search_members)) },
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

                    OutlinedButton(
                        onClick = onAddNewStaff,
                        modifier = Modifier.fillMaxWidth(),
                        shape = FormRoundedShape,
                        border = BorderStroke(1.dp, PitchGreen.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PitchGreen)
                    ) {
                        Text("+ ${stringResource(Res.string.btn_add_new_staff)}")
                    }

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
                                items(filtered, key = { it.userId }) { member ->
                                    MemberRow(
                                        member = member,
                                        selectedRole = uiState.selectedRoles[member.userId],
                                        onRoleTapped = { role -> onRoleTapped(member.userId, role) }
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
                        stringResource(Res.string.btn_add_staff_one)
                    } else {
                        stringResource(Res.string.btn_add_staff_other, selectedCount)
                    }
                    PrimaryLoadingButton(
                        text = addLabel,
                        submittingText = stringResource(Res.string.btn_adding_staff),
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
private fun MemberRow(
    member: ClubMemberSummary,
    selectedRole: TeamRole?,
    onRoleTapped: (TeamRole) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FormRoundedShape,
        color = if (selectedRole != null) PitchGreen.copy(alpha = 0.08f) else Color.White,
        border = BorderStroke(1.dp, if (selectedRole != null) PitchGreen else CarbonGray.copy(alpha = 0.15f))
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
                    text = "${member.firstName.take(1)}${member.lastName.take(1)}",
                    color = DeepCarbon,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${member.firstName} ${member.lastName}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            RolePill(stringResource(Res.string.label_role_coach), selectedRole == TeamRole.COACH) {
                onRoleTapped(TeamRole.COACH)
            }
            Spacer(modifier = Modifier.width(6.dp))
            RolePill(stringResource(Res.string.label_role_staff), selectedRole == TeamRole.STAFF) {
                onRoleTapped(TeamRole.STAFF)
            }
        }
    }
}

@Composable
private fun RolePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) PitchGreen else CarbonGray.copy(alpha = 0.1f),
        border = if (selected) null else BorderStroke(1.dp, CarbonGray.copy(alpha = 0.25f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (selected) Color.White else DeepCarbon.copy(alpha = 0.6f)
        )
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
                else Res.string.msg_no_eligible_members_title
            ),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon,
            textAlign = TextAlign.Center
        )
        if (!hasSearch) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.msg_no_eligible_members_hint),
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

private fun previewMember(id: String, first: String, last: String) = ClubMemberSummary(
    userId = id, firstName = first, lastName = last, email = "$first@t.com", role = ClubRole.MEMBER
)

@Preview
@Composable
private fun AddStaffToAgeGroupContentPreview() {
    ClubManagerTheme {
        AddStaffToAgeGroupContent(
            uiState = AddStaffToAgeGroupUiState.Idle(
                ageGroups = listOf(previewAgeGroup("a1", "Sub 2015", "s1", 2015)),
                seasons = listOf(previewSeason("s1", "26/27")),
                selectedAgeGroupId = "a1",
                eligibleMembers = listOf(
                    previewMember("p1", "Nuno", "Faria"),
                    previewMember("p2", "Rita", "Sousa")
                ),
                selectedRoles = mapOf("p1" to TeamRole.COACH)
            ),
            searchQuery = "",
            onNavigateBack = {},
            onSearchChange = {},
            onAgeGroupSelected = {},
            onRoleTapped = { _, _ -> },
            onAddNewStaff = {},
            onAddClick = {}
        )
    }
}

@Preview
@Composable
private fun AddStaffToAgeGroupContentEmptyPreview() {
    ClubManagerTheme(darkTheme = true) {
        AddStaffToAgeGroupContent(
            uiState = AddStaffToAgeGroupUiState.Idle(
                ageGroups = listOf(previewAgeGroup("a1", "Sub 2015", "s1", 2015)),
                seasons = listOf(previewSeason("s1", "26/27")),
                selectedAgeGroupId = "a1",
                eligibleMembers = emptyList()
            ),
            searchQuery = "",
            onNavigateBack = {},
            onSearchChange = {},
            onAgeGroupSelected = {},
            onRoleTapped = { _, _ -> },
            onAddNewStaff = {},
            onAddClick = {}
        )
    }
}
