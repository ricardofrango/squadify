package com.ricardofrango.squadify.presentation.ui.editclub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.model.ClubPermissionSetting
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.btn_save_changes
import squadify.app.shared.generated.resources.btn_save_permissions
import squadify.app.shared.generated.resources.btn_saving_permissions
import squadify.app.shared.generated.resources.ic_check
import squadify.app.shared.generated.resources.label_add_logo
import squadify.app.shared.generated.resources.label_club_address_field
import squadify.app.shared.generated.resources.label_club_name_field
import squadify.app.shared.generated.resources.label_contact_section
import squadify.app.shared.generated.resources.label_email
import squadify.app.shared.generated.resources.label_identity_section
import squadify.app.shared.generated.resources.label_permission_assign_players_to_age_group
import squadify.app.shared.generated.resources.label_permission_create_opponent
import squadify.app.shared.generated.resources.label_permission_create_players
import squadify.app.shared.generated.resources.label_permission_create_team
import squadify.app.shared.generated.resources.label_permission_edit_opponent
import squadify.app.shared.generated.resources.label_permission_edit_player
import squadify.app.shared.generated.resources.label_permission_level_coach
import squadify.app.shared.generated.resources.label_permission_level_coordinator_only
import squadify.app.shared.generated.resources.label_permission_level_staff
import squadify.app.shared.generated.resources.label_permission_manage_call_ups
import squadify.app.shared.generated.resources.label_permission_manage_live_match
import squadify.app.shared.generated.resources.label_permission_manage_match
import squadify.app.shared.generated.resources.label_phone_field
import squadify.app.shared.generated.resources.label_short_name_field
import squadify.app.shared.generated.resources.label_stadium_location_field
import squadify.app.shared.generated.resources.label_stadium_location_placeholder
import squadify.app.shared.generated.resources.label_where_section
import squadify.app.shared.generated.resources.msg_club_address_helper
import squadify.app.shared.generated.resources.msg_edit_club_details
import squadify.app.shared.generated.resources.msg_no_club_changes
import squadify.app.shared.generated.resources.msg_permission_assign_players_to_age_group_desc
import squadify.app.shared.generated.resources.msg_permission_create_opponent_desc
import squadify.app.shared.generated.resources.msg_permission_create_players_desc
import squadify.app.shared.generated.resources.msg_permission_create_team_desc
import squadify.app.shared.generated.resources.msg_permission_edit_opponent_desc
import squadify.app.shared.generated.resources.msg_permission_edit_player_desc
import squadify.app.shared.generated.resources.msg_permission_manage_call_ups_desc
import squadify.app.shared.generated.resources.msg_permission_manage_live_match_desc
import squadify.app.shared.generated.resources.msg_permission_manage_match_desc
import squadify.app.shared.generated.resources.msg_permissions_intro
import squadify.app.shared.generated.resources.msg_short_name_helper
import squadify.app.shared.generated.resources.msg_stadium_location_helper
import squadify.app.shared.generated.resources.msg_unsaved_club_changes
import squadify.app.shared.generated.resources.tab_club_details
import squadify.app.shared.generated.resources.tab_permissions
import squadify.app.shared.generated.resources.title_club_details

private val FormRoundedShape = RoundedCornerShape(18.dp)

/**
 * Tabbed club settings hub: "Club details" (dirty-tracked, checkmark-save-in-the-header, same pattern
 * as EditPlayerScreen/AccountScreen) and "Permissions" (each configurable action's minimum age-group-
 * staff role, with its own independent save action -- see TECHNICAL_DECISIONS.md on configurable
 * permissions). Reached from Club Details' bottom "More" menu. "Add logo" and "Stadium location" mirror
 * the design canvas artboard for this screen, with "Add logo" left non-functional (no image-upload
 * capability exists anywhere in the app yet, same reasoning as Account's "Change email").
 */
@Composable
fun EditClubScreen(
    clubId: String,
    onNavigateBack: () -> Unit,
    onClubUpdated: () -> Unit,
    viewModel: EditClubViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(clubId) { viewModel.load(clubId) }

    EditClubContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.load(clubId) },
        onTabSelected = { viewModel.onTabSelected(it) },
        onFormChange = { form -> viewModel.updateForm(form) },
        onSaveClick = { viewModel.onSaveClicked(clubId, onSaved = onClubUpdated) },
        onPermissionChanged = { action, minTeamRole -> viewModel.onPermissionChanged(action, minTeamRole) },
        onSavePermissionsClick = { viewModel.onSavePermissionsClicked(clubId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditClubContent(
    uiState: EditClubUiState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onFormChange: (ClubFormFields) -> Unit,
    onSaveClick: () -> Unit,
    onPermissionChanged: (ConfigurablePermission, TeamRole?) -> Unit,
    onSavePermissionsClick: () -> Unit
) {
    val idle = uiState as? EditClubUiState.Idle

    Scaffold(
        containerColor = Color.White,
        topBar = {
            Column(Modifier.background(CarbonGray)) {
                FMTopBar(
                    title = idle?.form?.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.title_club_details),
                    subtitle = stringResource(Res.string.msg_edit_club_details),
                    onNavigateBack = onNavigateBack,
                    actions = {
                        IconButton(onClick = onSaveClick, enabled = idle?.canSave == true) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check),
                                contentDescription = stringResource(Res.string.btn_save_changes),
                                tint = if (idle?.canSave == true) PitchGreen else Color.White.copy(
                                    alpha = 0.3f
                                )
                            )
                        }
                    }
                )
                if (idle != null) {
                    SecondaryTabRow(
                        selectedTabIndex = idle.selectedTab,
                        containerColor = CarbonGray,
                        contentColor = Color.White
                    ) {
                        Tab(
                            selected = idle.selectedTab == EDIT_CLUB_TAB_DETAILS,
                            onClick = { onTabSelected(EDIT_CLUB_TAB_DETAILS) },
                            text = { Text(stringResource(Res.string.tab_club_details)) },
                            selectedContentColor = PitchGreen,
                            unselectedContentColor = Color.White.copy(alpha = 0.6f)
                        )
                        Tab(
                            selected = idle.selectedTab == EDIT_CLUB_TAB_PERMISSIONS,
                            onClick = { onTabSelected(EDIT_CLUB_TAB_PERMISSIONS) },
                            text = { Text(stringResource(Res.string.tab_permissions)) },
                            selectedContentColor = PitchGreen,
                            unselectedContentColor = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        val boxModifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.White)
        when (uiState) {
            is EditClubUiState.Loading -> {
                Box(boxModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen)
                }
            }

            is EditClubUiState.Error -> {
                Column(
                    modifier = boxModifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.btn_retry)) }
                }
            }

            is EditClubUiState.Idle -> when (uiState.selectedTab) {
                EDIT_CLUB_TAB_PERMISSIONS -> PermissionsForm(
                    state = uiState,
                    modifier = boxModifier,
                    onPermissionChanged = onPermissionChanged,
                    onSaveClick = onSavePermissionsClick
                )
                else -> ClubDetailsForm(
                    state = uiState,
                    modifier = boxModifier,
                    onFormChange = onFormChange
                )
            }
        }
    }
}

@Composable
private fun PermissionsForm(
    state: EditClubUiState.Idle,
    modifier: Modifier,
    onPermissionChanged: (ConfigurablePermission, TeamRole?) -> Unit,
    onSaveClick: () -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(Res.string.msg_permissions_intro),
            style = MaterialTheme.typography.bodySmall,
            color = DeepCarbon.copy(alpha = 0.6f)
        )

        state.permissions.forEach { setting ->
            PermissionRow(
                setting = setting,
                onChange = { newValue -> onPermissionChanged(setting.action, newValue) }
            )
        }

        if (state.permissionsSubmitError != null) {
            Text(
                text = state.permissionsSubmitError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        PrimaryLoadingButton(
            text = stringResource(Res.string.btn_save_permissions),
            submittingText = stringResource(Res.string.btn_saving_permissions),
            isLoading = state.isSubmittingPermissions,
            enabled = state.canSavePermissions,
            onClick = onSaveClick
        )
    }
}

private fun permissionLabel(action: ConfigurablePermission) = when (action) {
    ConfigurablePermission.CREATE_PLAYERS -> Res.string.label_permission_create_players
    ConfigurablePermission.EDIT_PLAYER -> Res.string.label_permission_edit_player
    ConfigurablePermission.ASSIGN_PLAYERS_TO_AGE_GROUP -> Res.string.label_permission_assign_players_to_age_group
    ConfigurablePermission.CREATE_OPPONENT -> Res.string.label_permission_create_opponent
    ConfigurablePermission.EDIT_OPPONENT -> Res.string.label_permission_edit_opponent
    ConfigurablePermission.CREATE_TEAM -> Res.string.label_permission_create_team
    ConfigurablePermission.MANAGE_MATCH -> Res.string.label_permission_manage_match
    ConfigurablePermission.MANAGE_CALL_UPS -> Res.string.label_permission_manage_call_ups
    ConfigurablePermission.MANAGE_LIVE_MATCH -> Res.string.label_permission_manage_live_match
}

private fun permissionDescription(action: ConfigurablePermission) = when (action) {
    ConfigurablePermission.CREATE_PLAYERS -> Res.string.msg_permission_create_players_desc
    ConfigurablePermission.EDIT_PLAYER -> Res.string.msg_permission_edit_player_desc
    ConfigurablePermission.ASSIGN_PLAYERS_TO_AGE_GROUP -> Res.string.msg_permission_assign_players_to_age_group_desc
    ConfigurablePermission.CREATE_OPPONENT -> Res.string.msg_permission_create_opponent_desc
    ConfigurablePermission.EDIT_OPPONENT -> Res.string.msg_permission_edit_opponent_desc
    ConfigurablePermission.CREATE_TEAM -> Res.string.msg_permission_create_team_desc
    ConfigurablePermission.MANAGE_MATCH -> Res.string.msg_permission_manage_match_desc
    ConfigurablePermission.MANAGE_CALL_UPS -> Res.string.msg_permission_manage_call_ups_desc
    ConfigurablePermission.MANAGE_LIVE_MATCH -> Res.string.msg_permission_manage_live_match_desc
}

// null means "coordinator-only" -- the age-group-staff path is disabled entirely for this action.
private fun levelLabel(minTeamRole: TeamRole?) = when (minTeamRole) {
    TeamRole.STAFF -> Res.string.label_permission_level_staff
    TeamRole.COACH -> Res.string.label_permission_level_coach
    null -> Res.string.label_permission_level_coordinator_only
}

@Composable
private fun PermissionRow(
    setting: ClubPermissionSetting,
    onChange: (TeamRole?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(permissionLabel(setting.action)),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon
            )
            Text(
                text = stringResource(permissionDescription(setting.action)),
                style = MaterialTheme.typography.bodySmall,
                color = DeepCarbon.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(10.dp))
            Box {
                Surface(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = CarbonGray.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp).fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(levelLabel(setting.minTeamRole)),
                            color = DeepCarbon,
                            fontWeight = FontWeight.Bold
                        )
                        Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.label_permission_level_staff), color = DeepCarbon) },
                        onClick = { onChange(TeamRole.STAFF); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.label_permission_level_coach), color = DeepCarbon) },
                        onClick = { onChange(TeamRole.COACH); expanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.label_permission_level_coordinator_only), color = DeepCarbon) },
                        onClick = { onChange(null); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClubDetailsForm(
    state: EditClubUiState.Idle,
    modifier: Modifier,
    onFormChange: (ClubFormFields) -> Unit
) {
    val form = state.form

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ClubIdentityHeader(state = state)

        Text(
            text = stringResource(Res.string.label_identity_section),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon.copy(alpha = 0.6f)
        )
        FormField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = stringResource(Res.string.label_club_name_field)
        )
        FormField(
            value = form.shortName,
            onValueChange = { onFormChange(form.copy(shortName = it)) },
            label = stringResource(Res.string.label_short_name_field),
            infoText = stringResource(Res.string.msg_short_name_helper),
        )

        HorizontalDivider(color = CarbonGray.copy(alpha = 0.15f))

        Text(
            text = stringResource(Res.string.label_contact_section),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon.copy(alpha = 0.6f)
        )
        FormField(
            value = form.email,
            onValueChange = { onFormChange(form.copy(email = it)) },
            label = stringResource(Res.string.label_email),
            keyboardType = KeyboardType.Email
        )
        FormField(
            value = form.phone,
            onValueChange = { onFormChange(form.copy(phone = it)) },
            label = stringResource(Res.string.label_phone_field),
            keyboardType = KeyboardType.Phone
        )

        HorizontalDivider(color = CarbonGray.copy(alpha = 0.15f))

        Text(
            text = stringResource(Res.string.label_where_section),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepCarbon.copy(alpha = 0.6f)
        )
        FormField(
            value = form.address,
            onValueChange = { onFormChange(form.copy(address = it)) },
            label = stringResource(Res.string.label_club_address_field),
            infoText = stringResource(Res.string.msg_club_address_helper),
        )
        FormField(
            value = form.stadiumLocation,
            onValueChange = { onFormChange(form.copy(stadiumLocation = it)) },
            label = stringResource(Res.string.label_stadium_location_field),
            placeholder = stringResource(Res.string.label_stadium_location_placeholder),
            infoText = stringResource(Res.string.msg_stadium_location_helper)
        )

        if (state.submitError != null) {
            Text(
                text = state.submitError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ClubIdentityHeader(modifier: Modifier = Modifier, state: EditClubUiState.Idle) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(56.dp).background(PitchGreen.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.initials,
                color = PitchGreen,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(Res.string.label_add_logo),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PitchGreen
            )
            Text(
                text = stringResource(
                    if (state.isDirty) Res.string.msg_unsaved_club_changes else Res.string.msg_no_club_changes
                ),
                style = MaterialTheme.typography.bodySmall,
                color = DeepCarbon.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun FormField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    infoText: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = FormRoundedShape,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DeepCarbon,
                unfocusedTextColor = DeepCarbon,
                focusedBorderColor = PitchGreen,
                unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
                focusedLabelColor = PitchGreen,
                unfocusedLabelColor = CarbonGray
            )
        )
        infoText?.let {
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = DeepCarbon.copy(alpha = 0.5f)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun sampleFields() = ClubFormFields(
    name = "Clube Desportivo Ribamar",
    shortName = "CDR",
    email = "geral@cdribamar.pt",
    phone = "+351 261 000 123",
    address = "Rua do Clube 14, 2640-000 Ribamar, Mafra",
    stadiumLocation = ""
)

private fun samplePermissions() = ConfigurablePermission.entries.map {
    ClubPermissionSetting(it, if (it == ConfigurablePermission.MANAGE_LIVE_MATCH) TeamRole.STAFF else TeamRole.COACH)
}

@Preview
@Composable
private fun EditClubDetailsPreview() {
    SquadifyTheme {
        val fields = sampleFields()
        val permissions = samplePermissions()
        EditClubContent(
            uiState = EditClubUiState.Idle(original = fields, form = fields, originalPermissions = permissions, permissions = permissions),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {},
            onPermissionChanged = { _, _ -> },
            onSavePermissionsClick = {}
        )
    }
}

@Preview
@Composable
private fun EditClubDetailsDirtyPreview() {
    SquadifyTheme {
        val fields = sampleFields()
        val permissions = samplePermissions()
        EditClubContent(
            uiState = EditClubUiState.Idle(
                original = fields,
                form = fields.copy(stadiumLocation = "Estrada da Praia, Ribamar"),
                originalPermissions = permissions,
                permissions = permissions
            ),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {},
            onPermissionChanged = { _, _ -> },
            onSavePermissionsClick = {}
        )
    }
}

@Preview
@Composable
private fun EditClubPermissionsTabPreview() {
    SquadifyTheme {
        val fields = sampleFields()
        val permissions = samplePermissions()
        EditClubContent(
            uiState = EditClubUiState.Idle(
                selectedTab = EDIT_CLUB_TAB_PERMISSIONS,
                original = fields,
                form = fields,
                originalPermissions = permissions,
                permissions = permissions
            ),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {},
            onPermissionChanged = { _, _ -> },
            onSavePermissionsClick = {}
        )
    }
}

@Preview
@Composable
private fun EditClubPermissionsTabDirtyPreview() {
    SquadifyTheme {
        val fields = sampleFields()
        val permissions = samplePermissions()
        EditClubContent(
            uiState = EditClubUiState.Idle(
                selectedTab = EDIT_CLUB_TAB_PERMISSIONS,
                original = fields,
                form = fields,
                originalPermissions = permissions,
                permissions = permissions.map {
                    if (it.action == ConfigurablePermission.MANAGE_MATCH) it.copy(minTeamRole = null) else it
                }
            ),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {},
            onPermissionChanged = { _, _ -> },
            onSavePermissionsClick = {}
        )
    }
}

@Preview
@Composable
private fun EditClubLoadingPreview() {
    SquadifyTheme {
        EditClubContent(
            uiState = EditClubUiState.Loading,
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {},
            onPermissionChanged = { _, _ -> },
            onSavePermissionsClick = {}
        )
    }
}

@Preview
@Composable
private fun EditClubErrorPreview() {
    SquadifyTheme {
        EditClubContent(
            uiState = EditClubUiState.Error("Couldn't load this club. Please try again."),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {},
            onPermissionChanged = { _, _ -> },
            onSavePermissionsClick = {}
        )
    }
}
