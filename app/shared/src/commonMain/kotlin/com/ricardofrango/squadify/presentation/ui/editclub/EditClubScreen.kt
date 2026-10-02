package com.ricardofrango.squadify.presentation.ui.editclub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.ricardofrango.squadify.model.ClubMemberEntry
import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.MemberAgeGroupRole
import com.ricardofrango.squadify.model.MemberLinkedPlayer
import com.ricardofrango.squadify.model.UserPlayerRelationship
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.PendingClubInvite
import com.ricardofrango.squadify.presentation.ui.components.DetailBottomBar
import com.ricardofrango.squadify.presentation.ui.components.DetailRail
import com.ricardofrango.squadify.presentation.ui.components.DetailTab
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.WhiteSmoke
import org.jetbrains.compose.resources.StringResource
import squadify.app.shared.generated.resources.btn_cancel
import squadify.app.shared.generated.resources.club_discard_body
import squadify.app.shared.generated.resources.club_discard_both
import squadify.app.shared.generated.resources.club_discard_confirm
import squadify.app.shared.generated.resources.club_discard_details
import squadify.app.shared.generated.resources.club_discard_permissions
import squadify.app.shared.generated.resources.club_discard_title
import squadify.app.shared.generated.resources.club_settings
import squadify.app.shared.generated.resources.club_tab_details
import squadify.app.shared.generated.resources.club_tab_members
import squadify.app.shared.generated.resources.club_tab_permissions
import squadify.app.shared.generated.resources.ic_info
import squadify.app.shared.generated.resources.ic_lock
import squadify.app.shared.generated.resources.ic_player
import squadify.app.shared.generated.resources.members_club_role
import squadify.app.shared.generated.resources.members_invite_helper
import squadify.app.shared.generated.resources.members_invite_title
import squadify.app.shared.generated.resources.members_role_admin_desc
import squadify.app.shared.generated.resources.members_role_coordinator_desc
import squadify.app.shared.generated.resources.members_role_member_desc
import squadify.app.shared.generated.resources.members_send_invite
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
import androidx.compose.material3.Surface
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
import squadify.app.shared.generated.resources.title_club_details

private val FormRoundedShape = RoundedCornerShape(18.dp)

/**
 * Club settings (design 34): Details · Members · Permissions in the dark bottom bar (a rail in
 * landscape), always opening on Details. Details is dirty-tracked with the ✓ save in the header -- the
 * ✓ only shows on Details, since it saves nothing else; Permissions keeps its own Save button. Edits on
 * either survive tab switches and mark the tab with an amber dot; leaving with any of them unsaved asks
 * once, naming what would be lost. Members lists everyone by club role with search, a role filter and
 * the pending invites, and invites new members as Member or Coordinator. Reached from Club Details'
 * bottom "More" menu. "Add logo" stays non-functional (no image upload anywhere in the app yet).
 */
@Composable
fun EditClubScreen(
    clubId: String,
    onNavigateBack: () -> Unit,
    onClubUpdated: () -> Unit,
    onOpenMember: (userId: String) -> Unit = {},
    onOpenInvite: (inviteId: String) -> Unit = {},
    viewModel: EditClubViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    val members by viewModel.members.collectAsState()
    val inviting by viewModel.inviting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(clubId) { viewModel.load(clubId) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    EditClubContent(
        uiState = uiState,
        members = members,
        inviting = inviting,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.load(clubId) },
        onTabSelected = { viewModel.onTabSelected(it) },
        onFormChange = { form -> viewModel.updateForm(form) },
        onSaveClick = { viewModel.onSaveClicked(clubId, onSaved = onClubUpdated) },
        onPermissionChanged = { action, minTeamRole -> viewModel.onPermissionChanged(action, minTeamRole) },
        onSavePermissionsClick = { viewModel.onSavePermissionsClicked(clubId) },
        onRetryMembers = { viewModel.loadMembers(clubId) },
        onInvite = { email, role, onDone -> viewModel.invite(clubId, email, role, onDone) },
        onOpenMember = { onOpenMember(it.userId) },
        onOpenInvite = { onOpenInvite(it.inviteId) },
        onResend = { viewModel.resendInvite(clubId, it.inviteId, it.email) },
        onCancelInvite = { viewModel.cancelInvite(clubId, it.inviteId, it.email) }
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
    onSavePermissionsClick: () -> Unit,
    members: MembersUiState = MembersUiState.Loading,
    inviting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onRetryMembers: () -> Unit = {},
    onInvite: (email: String, role: ClubRole, onDone: () -> Unit) -> Unit = { _, _, _ -> },
    onOpenMember: (ClubMemberEntry) -> Unit = {},
    onOpenInvite: (PendingClubInvite) -> Unit = {},
    onResend: (PendingClubInvite) -> Unit = {},
    onCancelInvite: (PendingClubInvite) -> Unit = {},
    initialFilter: MembersFilter = MembersFilter.ALL
) {
    val idle = uiState as? EditClubUiState.Idle
    // The Members tab's search and filter live with the screen, so they survive tab switches.
    var filter by remember { mutableStateOf(initialFilter) }
    var query by remember { mutableStateOf("") }
    var showInvite by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }

    val detailsDirty = idle?.isDirty == true
    val permissionsDirty = idle?.permissionsDirty == true
    val requestBack = { if (detailsDirty || permissionsDirty) showDiscard = true else onNavigateBack() }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = detailsDirty || permissionsDirty,
        onBackCompleted = { showDiscard = true }
    )

    val tabs = listOf(
        DetailTab(Res.drawable.ic_info, Res.string.club_tab_details, dot = detailsDirty),
        DetailTab(Res.drawable.ic_player, Res.string.club_tab_members),
        DetailTab(Res.drawable.ic_lock, Res.string.club_tab_permissions, dot = permissionsDirty)
    )
    val selectedTab = idle?.selectedTab ?: EDIT_CLUB_TAB_DETAILS

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (landscape && idle != null) DetailRail(tabs, selectedTab, onTabSelected)
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = if (selectedTab == EDIT_CLUB_TAB_MEMBERS) WhiteSmoke else Color.White,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    FMTopBar(
                        title = idle?.form?.name?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.title_club_details),
                        subtitle = stringResource(Res.string.club_settings),
                        onNavigateBack = requestBack,
                        actions = {
                            if (idle != null && selectedTab == EDIT_CLUB_TAB_DETAILS) {
                                IconButton(onClick = onSaveClick, enabled = idle.canSave) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_check),
                                        contentDescription = stringResource(Res.string.btn_save_changes),
                                        tint = if (idle.canSave) PitchGreen else Color.White.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = { if (!landscape && idle != null) DetailBottomBar(tabs, selectedTab, onTabSelected) }
            ) { paddingValues ->
                val boxModifier = Modifier.fillMaxSize().padding(paddingValues)
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
                        EDIT_CLUB_TAB_MEMBERS -> ClubMembersTab(
                            state = members,
                            filter = filter,
                            query = query,
                            sideBySide = landscape,
                            modifier = boxModifier,
                            onFilterChange = { filter = it },
                            onQueryChange = { query = it },
                            onInvite = { showInvite = true },
                            onRetry = onRetryMembers,
                            onOpenMember = onOpenMember,
                            onOpenInvite = onOpenInvite,
                            onResend = onResend,
                            onCancelInvite = onCancelInvite
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
    }

    if (showInvite) {
        InviteMemberSheet(
            inviting = inviting,
            onDismiss = { showInvite = false },
            onSend = { email, role -> onInvite(email, role) { showInvite = false } }
        )
    }
    if (showDiscard) {
        val what = stringResource(
            when {
                detailsDirty && permissionsDirty -> Res.string.club_discard_both
                permissionsDirty -> Res.string.club_discard_permissions
                else -> Res.string.club_discard_details
            }
        )
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            containerColor = Color.White,
            title = { Text(stringResource(Res.string.club_discard_title)) },
            text = { Text(stringResource(Res.string.club_discard_body, what)) },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onNavigateBack() }) {
                    Text(stringResource(Res.string.club_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(stringResource(Res.string.btn_cancel)) } }
        )
    }
}

/** "+ Invite": email and club role -- Member or Coordinator; Admin is only granted once they've joined (34b). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteMemberSheet(inviting: Boolean, onDismiss: () -> Unit, onSend: (String, ClubRole) -> Unit) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(ClubRole.MEMBER) }
    val valid = email.trim().let { it.contains('@') && it.substringAfter('@').contains('.') && !it.contains(' ') }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(Res.string.members_invite_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            FormField(
                value = email,
                onValueChange = { email = it },
                label = "${stringResource(Res.string.label_email)} *",
                keyboardType = KeyboardType.Email
            )
            Text(
                stringResource(Res.string.members_club_role).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon.copy(alpha = 0.55f)
            )
            listOf(ClubRole.MEMBER, ClubRole.COORDINATOR).forEach { option ->
                RoleOption(option, selected = option == role, enabled = true, reason = null) { role = option }
            }
            Text(stringResource(Res.string.members_invite_helper), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.55f))
            PrimaryLoadingButton(
                text = stringResource(Res.string.members_send_invite),
                submittingText = stringResource(Res.string.members_send_invite),
                isLoading = inviting,
                enabled = valid && !inviting,
                onClick = { onSend(email.trim(), role) }
            )
        }
    }
}

fun ClubRole.descriptionRes(): StringResource = when (this) {
    ClubRole.CLUB_ADMIN -> Res.string.members_role_admin_desc
    ClubRole.COORDINATOR -> Res.string.members_role_coordinator_desc
    ClubRole.MEMBER -> Res.string.members_role_member_desc
}

/** A radio row for a club role with its one-line description, and an amber [reason] when it's locked. */
@Composable
internal fun RoleOption(role: ClubRole, selected: Boolean, enabled: Boolean, reason: String?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) PitchGreen.copy(alpha = 0.08f) else Color.White,
        border = BorderStroke(1.5.dp, if (selected) PitchGreen else CarbonGray.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(selectedColor = PitchGreen, unselectedColor = CarbonGray.copy(alpha = 0.4f))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(role.labelRes()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (enabled) DeepCarbon else DeepCarbon.copy(alpha = 0.4f)
                )
                Text(
                    stringResource(role.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = if (enabled) 0.6f else 0.35f)
                )
                if (reason != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(reason, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = AmberText)
                }
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

internal fun sampleMembersOverview(): ClubMembersOverview {
    val joined = Instant.parse("2025-08-01T10:00:00Z")
    fun member(id: String, first: String, last: String, role: ClubRole, roles: List<MemberAgeGroupRole> = emptyList(), linked: List<MemberLinkedPlayer> = emptyList()) =
        ClubMemberEntry(id, first, last, "${first.lowercase()}.${last.lowercase()}@gmail.com", null, role, roles, linked, joined)
    fun coach(name: String) = MemberAgeGroupRole("ag-$name", name, "2025/26", TeamRole.COACH, currentSeason = true)
    return ClubMembersOverview(
        members = listOf(
            member("u1", "Ricardo", "Frango", ClubRole.CLUB_ADMIN, listOf(coach("Sub-15"))),
            member("u2", "Marta", "Leal", ClubRole.COORDINATOR),
            member("u3", "Rita", "Sá", ClubRole.COORDINATOR, listOf(coach("Sub-15"))),
            member("u4", "João", "Camelo", ClubRole.MEMBER, listOf(coach("Sub-15"), coach("Sub-17"))),
            member("u5", "Sofia", "Bragança", ClubRole.MEMBER, linked = listOf(MemberLinkedPlayer("p1", "Tomás Bragança", UserPlayerRelationship.MOTHER, "Sub-15", 7))),
            member("u6", "Helena", "Vaz", ClubRole.MEMBER)
        ),
        pendingInvites = listOf(
            PendingClubInvite("i1", "carla.sousa@gmail.com", ClubRole.MEMBER, Clock.System.now() - 3.days),
            PendingClubInvite("i2", "nuno.ferreira@sapo.pt", ClubRole.COORDINATOR, Clock.System.now() - 9.days)
        ),
        viewerUserId = "u1",
        viewerRole = ClubRole.CLUB_ADMIN
    )
}

@Composable
private fun MembersPreviewFrame(members: MembersUiState, filter: MembersFilter = MembersFilter.ALL) {
    SquadifyTheme {
        val fields = sampleFields()
        val permissions = samplePermissions()
        EditClubContent(
            uiState = EditClubUiState.Idle(selectedTab = EDIT_CLUB_TAB_MEMBERS, original = fields, form = fields, originalPermissions = permissions, permissions = permissions),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {},
            onPermissionChanged = { _, _ -> },
            onSavePermissionsClick = {},
            members = members,
            initialFilter = filter
        )
    }
}

@Preview
@Composable
private fun EditClubMembersPreview() = MembersPreviewFrame(MembersUiState.Loaded(sampleMembersOverview()))

@Preview
@Composable
private fun EditClubMembersOnlyYouPreview() {
    val overview = sampleMembersOverview()
    MembersPreviewFrame(MembersUiState.Loaded(overview.copy(members = overview.members.take(1), pendingInvites = emptyList())))
}

@Preview
@Composable
private fun EditClubMembersLoadingPreview() = MembersPreviewFrame(MembersUiState.Loading)

@Preview
@Composable
private fun EditClubMembersErrorPreview() = MembersPreviewFrame(MembersUiState.Error)
