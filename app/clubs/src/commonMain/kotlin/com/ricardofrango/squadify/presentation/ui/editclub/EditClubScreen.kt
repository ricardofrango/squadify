package com.ricardofrango.squadify.presentation.ui.editclub

import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.besideRail
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import com.ricardofrango.squadify.presentation.ui.components.useNavigationRail
import com.ricardofrango.squadify.presentation.ui.components.isLandscapeLayout
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.ricardofrango.squadify.model.ClubMemberEntry
import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.MemberAgeGroupRole
import com.ricardofrango.squadify.model.MemberLinkedPlayer
import com.ricardofrango.squadify.model.UserPlayerRelationship
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import com.ricardofrango.squadify.model.ClubRole
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.readBytes
import com.ricardofrango.squadify.presentation.ui.components.ClubLogo
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import squadify.app.shared.generated.resources.label_change_logo
import squadify.app.shared.generated.resources.btn_remove_logo
import squadify.app.shared.generated.resources.msg_logo_saving
import squadify.app.shared.generated.resources.msg_logo_hint
import com.ricardofrango.squadify.model.PendingClubInvite
import com.ricardofrango.squadify.presentation.ui.components.DetailBottomBar
import com.ricardofrango.squadify.presentation.ui.components.DetailRail
import com.ricardofrango.squadify.presentation.ui.components.DetailTab
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
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
import com.ricardofrango.squadify.model.defaultSetting
import com.ricardofrango.squadify.model.isClubLevel
import squadify.app.shared.generated.resources.label_permission_manage_player_status
import squadify.app.shared.generated.resources.msg_permission_manage_player_status_desc
import squadify.app.shared.generated.resources.label_permission_level_admins_coordinators
import squadify.app.shared.generated.resources.label_permission_level_admins_only
import squadify.app.shared.generated.resources.msg_permission_admin_only_setting
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
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
import squadify.app.shared.generated.resources.label_permission_manage_training
import squadify.app.shared.generated.resources.label_permission_manage_match
import squadify.app.shared.generated.resources.label_phone_field
import squadify.app.shared.generated.resources.label_short_name_field
import squadify.app.shared.generated.resources.label_stadium_location_field
import squadify.app.shared.generated.resources.label_stadium_location_placeholder
import squadify.app.shared.generated.resources.label_where_section
import squadify.app.shared.generated.resources.msg_club_address_helper
import squadify.app.shared.generated.resources.msg_permission_assign_players_to_age_group_desc
import squadify.app.shared.generated.resources.msg_permission_create_opponent_desc
import squadify.app.shared.generated.resources.msg_permission_create_players_desc
import squadify.app.shared.generated.resources.msg_permission_create_team_desc
import squadify.app.shared.generated.resources.msg_permission_edit_opponent_desc
import squadify.app.shared.generated.resources.msg_permission_edit_player_desc
import squadify.app.shared.generated.resources.msg_permission_manage_call_ups_desc
import squadify.app.shared.generated.resources.msg_permission_manage_live_match_desc
import squadify.app.shared.generated.resources.msg_permission_manage_training_desc
import squadify.app.shared.generated.resources.msg_permission_manage_match_desc
import squadify.app.shared.generated.resources.msg_permissions_intro
import squadify.app.shared.generated.resources.msg_short_name_helper
import squadify.app.shared.generated.resources.msg_stadium_location_helper
import squadify.app.shared.generated.resources.msg_unsaved_club_changes
import squadify.app.shared.generated.resources.title_club_details
import squadify.app.shared.generated.resources.country_portugal
import squadify.app.shared.generated.resources.msg_country_helper
import squadify.app.shared.generated.resources.label_country_field
import com.ricardofrango.squadify.model.Country

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
    // Set by Member details when the list underneath went stale, with an optional message to show.
    refreshMembers: Boolean = false,
    membersMessage: String? = null,
    onMembersRefreshed: () -> Unit = {},
    viewModel: EditClubViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()
        val members by viewModel.members.collectAsStateWithLifecycle()
        val inviting by viewModel.inviting.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        // Loads once per club: coming back from Member details must not reset unsaved Details/Permissions edits.
        LaunchedEffect(clubId) { viewModel.load(clubId) }
        LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }
        LaunchedEffect(refreshMembers, membersMessage) {
            if (refreshMembers) {
                viewModel.loadMembers(clubId)
                val message = membersMessage
                onMembersRefreshed()
                if (message != null) snackbarHostState.showSnackbar(message)
            }
        }

        EditClubContent(
            uiState = uiState,
            members = members,
            inviting = inviting,
            snackbarHostState = snackbarHostState,
            onNavigateBack = onNavigateBack,
            onRetry = { viewModel.load(clubId, force = true) },
            onTabSelected = { viewModel.onTabSelected(it) },
            onFormChange = { form -> viewModel.updateForm(form) },
            onSaveClick = { viewModel.onSaveClicked(clubId, onSaved = onClubUpdated) },
            onLogoPicked = { bytes -> viewModel.onLogoPicked(clubId, bytes, onChanged = onClubUpdated) },
            onRemoveLogo = { viewModel.onRemoveLogo(clubId, onChanged = onClubUpdated) },
            onPermissionChanged = viewModel::onPermissionChanged,
            onSavePermissionsClick = { viewModel.onSavePermissionsClicked(clubId) },
            onRetryMembers = { viewModel.loadMembers(clubId) },
            onMembersFilter = { filter, query -> viewModel.onMembersFilter(clubId, filter, query) },
            onLoadMoreMembers = { viewModel.loadMoreMembers(clubId) },
            onInvite = { email, role, onDone -> viewModel.invite(clubId, email, role, onDone) },
            onOpenMember = { onOpenMember(it.userId) },
            onOpenInvite = { onOpenInvite(it.inviteId) },
            onResend = { viewModel.resendInvite(clubId, it.inviteId, it.email) },
            onCancelInvite = { viewModel.cancelInvite(clubId, it.inviteId, it.email) }
        )
    }
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
    onPermissionChanged: (ClubPermissionSetting) -> Unit,
    onSavePermissionsClick: () -> Unit,
    members: MembersUiState = MembersUiState.Loading,
    inviting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onRetryMembers: () -> Unit = {},
    // The Members tab's role filter and search run on the server, which sends a page at a time.
    onMembersFilter: (MembersFilter, String) -> Unit = { _, _ -> },
    onLoadMoreMembers: () -> Unit = {},
    onInvite: (email: String, role: ClubRole, onDone: () -> Unit) -> Unit = { _, _, _ -> },
    onOpenMember: (ClubMemberEntry) -> Unit = {},
    onOpenInvite: (PendingClubInvite) -> Unit = {},
    onResend: (PendingClubInvite) -> Unit = {},
    onCancelInvite: (PendingClubInvite) -> Unit = {},
    onLogoPicked: (ByteArray) -> Unit = {},
    onRemoveLogo: () -> Unit = {},
    initialFilter: MembersFilter = MembersFilter.ALL
) {
    val idle = uiState as? EditClubUiState.Idle
    // The Members tab's search and filter live with the screen, so they survive tab switches.
    var filter by remember { mutableStateOf(initialFilter) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(filter, query) { onMembersFilter(filter, query) }
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
        val landscape = isLandscapeLayout
        val rail = useNavigationRail()
        Row(Modifier.fillMaxSize()) {
            if (rail && idle != null) DetailRail(tabs, selectedTab, onTabSelected)
            Scaffold(
                contentWindowInsets = ScreenWindowInsets,
                modifier = Modifier.weight(1f).besideRail(rail && idle != null),
                containerColor = ScreenBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    SquadifyTopBar(
                        title = idle?.form?.name?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.title_club_details),
                        subtitle = stringResource(Res.string.club_settings),
                        onNavigateBack = requestBack,
                        actions = {
                            if (idle != null && selectedTab == EDIT_CLUB_TAB_DETAILS) {
                                IconButton(onClick = onSaveClick, enabled = idle.canSave) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_check),
                                        contentDescription = stringResource(Res.string.btn_save_changes),
                                        tint = if (idle.canSave) ChromeAccent else Color.White.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = { if (!rail && idle != null) DetailBottomBar(tabs, selectedTab, onTabSelected) }
            ) { paddingValues ->
                val boxModifier = Modifier.fillMaxSize().padding(paddingValues)
                when (uiState) {
                    is EditClubUiState.Loading -> {
                        Box(boxModifier, contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PitchGreen)
                        }
                    }

                    is EditClubUiState.Error -> {
                        ErrorScreen(title = uiState.message, body = null, onRetry = onRetry, modifier = boxModifier)
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
                            onCancelInvite = onCancelInvite,
                            onLoadMore = onLoadMoreMembers
                        )
                        else -> ClubDetailsForm(
                            state = uiState,
                            modifier = boxModifier,
                            onFormChange = onFormChange,
                            onLogoPicked = onLogoPicked,
                            onRemoveLogo = onRemoveLogo,
                            onSubmit = { if (uiState.canSave) onSaveClick() }
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
            titleContentColor = DeepCarbon,
            textContentColor = DeepCarbon.copy(alpha = 0.75f),
            title = { Text(stringResource(Res.string.club_discard_title)) },
            text = { Text(stringResource(Res.string.club_discard_body, what)) },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onNavigateBack() }) {
                    Text(stringResource(Res.string.club_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text(stringResource(Res.string.btn_cancel), color = CarbonGray) } }
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
                keyboardType = KeyboardType.Email,
                // A role is picked below the email, so the keyboard key only closes the keyboard.
                imeAction = ImeAction.Done
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
    onPermissionChanged: (ClubPermissionSetting) -> Unit,
    onSaveClick: () -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(Res.string.msg_permissions_intro),
            style = MaterialTheme.typography.bodySmall,
            color = DeepCarbon.copy(alpha = 0.6f)
        )

        state.permissions.forEach { setting ->
            if (setting.action.isClubLevel) {
                ClubLevelPermissionRow(
                    setting = setting,
                    enabled = state.canChangeClubLevelPermissions,
                    onChange = { role -> onPermissionChanged(setting.copy(minClubRole = role)) }
                )
            } else {
                PermissionRow(
                    setting = setting,
                    onChange = { newValue -> onPermissionChanged(setting.copy(minAgeGroupRole = newValue)) }
                )
            }
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
    ConfigurablePermission.MANAGE_TRAINING -> Res.string.label_permission_manage_training
    ConfigurablePermission.MANAGE_PLAYER_STATUS -> Res.string.label_permission_manage_player_status
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
    ConfigurablePermission.MANAGE_TRAINING -> Res.string.msg_permission_manage_training_desc
    ConfigurablePermission.MANAGE_PLAYER_STATUS -> Res.string.msg_permission_manage_player_status_desc
}

// null means "coordinator-only" -- the age-group-staff path is disabled entirely for this action.
private fun levelLabel(minAgeGroupRole: AgeGroupRole?) = when (minAgeGroupRole) {
    AgeGroupRole.STAFF -> Res.string.label_permission_level_staff
    AgeGroupRole.COACH -> Res.string.label_permission_level_coach
    null -> Res.string.label_permission_level_coordinator_only
}

@Composable
private fun PermissionRow(
    setting: ClubPermissionSetting,
    onChange: (AgeGroupRole?) -> Unit
) {
    PermissionCard(
        action = setting.action,
        selected = stringResource(levelLabel(setting.minAgeGroupRole)),
        options = listOf(
            Res.string.label_permission_level_staff to { onChange(AgeGroupRole.STAFF) },
            Res.string.label_permission_level_coach to { onChange(AgeGroupRole.COACH) },
            Res.string.label_permission_level_coordinator_only to { onChange(null) }
        )
    )
}

/** A club-level action (who disables and reactivates players): admins and coordinators, or admins only. */
@Composable
private fun ClubLevelPermissionRow(
    setting: ClubPermissionSetting,
    enabled: Boolean,
    onChange: (ClubRole) -> Unit
) {
    PermissionCard(
        action = setting.action,
        selected = stringResource(
            if (setting.minClubRole == ClubRole.CLUB_ADMIN) Res.string.label_permission_level_admins_only
            else Res.string.label_permission_level_admins_coordinators
        ),
        options = listOf(
            Res.string.label_permission_level_admins_coordinators to { onChange(ClubRole.COORDINATOR) },
            Res.string.label_permission_level_admins_only to { onChange(ClubRole.CLUB_ADMIN) }
        ),
        enabled = enabled,
        // A coordinator sees the admin's choice but can't change it.
        footnote = if (enabled) null else stringResource(Res.string.msg_permission_admin_only_setting)
    )
}

@Composable
private fun PermissionCard(
    action: ConfigurablePermission,
    selected: String,
    options: List<Pair<StringResource, () -> Unit>>,
    enabled: Boolean = true,
    footnote: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(permissionLabel(action)),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon
            )
            Text(
                text = stringResource(permissionDescription(action)),
                style = MaterialTheme.typography.bodySmall,
                color = DeepCarbon.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(10.dp))
            Box {
                Surface(
                    onClick = { expanded = true },
                    enabled = enabled,
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
                            text = selected,
                            color = DeepCarbon.copy(alpha = if (enabled) 1f else 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                        if (enabled) Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White)) {
                    options.forEach { (label, onClick) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label), color = DeepCarbon) },
                            onClick = { onClick(); expanded = false }
                        )
                    }
                }
            }
            if (footnote != null) {
                Spacer(Modifier.height(6.dp))
                Text(footnote, style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun ClubDetailsForm(
    state: EditClubUiState.Idle,
    modifier: Modifier,
    onFormChange: (ClubFormFields) -> Unit,
    onSubmit: () -> Unit,
    onLogoPicked: (ByteArray) -> Unit = {},
    onRemoveLogo: () -> Unit = {}
) {
    val form = state.form

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ClubIdentityHeader(state = state, onLogoPicked = onLogoPicked, onRemoveLogo = onRemoveLogo)

        SectionCard(spacing = 16.dp) {
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
        }

        SectionCard(spacing = 16.dp) {
            ClubColoursSection(
                theme = form.theme,
                problem = form.themeProblem,
                clubName = form.name.ifBlank { stringResource(Res.string.title_club_details) },
                initials = state.initials,
                onChange = { onFormChange(form.copy(theme = it)) }
            )
        }

        SectionCard(spacing = 16.dp) {
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
        }

        SectionCard(spacing = 16.dp) {
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
                infoText = stringResource(Res.string.msg_stadium_location_helper),
                onSubmit = onSubmit
            )
            CountryField(form.country) { onFormChange(form.copy(country = it)) }
        }

        if (state.submitError != null) {
            Text(
                text = state.submitError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** The club's country, for its public holidays when creating training sessions (design 39k). */
@Composable
private fun CountryField(country: Country, onChange: (Country) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Box {
            Surface(
                onClick = { open = true },
                shape = FormRoundedShape,
                color = Color.White,
                border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.label_country_field), style = MaterialTheme.typography.labelSmall, color = CarbonGray)
                        Text(stringResource(country.nameRes()), style = MaterialTheme.typography.bodyLarge, color = DeepCarbon)
                    }
                    Text("▾", color = DeepCarbon.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
            }
            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(Color.White)) {
                Country.entries.forEach { c ->
                    DropdownMenuItem(text = { Text(stringResource(c.nameRes()), color = DeepCarbon) }, onClick = { open = false; onChange(c) })
                }
            }
        }
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = stringResource(Res.string.msg_country_helper),
            style = MaterialTheme.typography.bodySmall,
            color = DeepCarbon.copy(alpha = 0.5f)
        )
    }
}

private fun Country.nameRes() = when (this) {
    Country.PORTUGAL -> Res.string.country_portugal
}

/**
 * The logo (or initials) with "Add logo" / "Change logo" and "Remove". The image picker opens the
 * platform's gallery; the logo is saved straight away, apart from the ✓ that saves the fields below.
 */
@Composable
private fun ClubIdentityHeader(
    modifier: Modifier = Modifier,
    state: EditClubUiState.Idle,
    onLogoPicked: (ByteArray) -> Unit,
    onRemoveLogo: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch { onLogoPicked(file.readBytes()) }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            ClubLogo(
                logoUrl = state.logoUrl,
                initials = state.initials,
                size = 56.dp,
                background = PitchGreen.copy(alpha = 0.15f),
                initialsColor = PitchGreen
            )
            if (state.isUpdatingLogo) CircularProgressIndicator(Modifier.size(56.dp), color = PitchGreen, strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(if (state.logoUrl == null) Res.string.label_add_logo else Res.string.label_change_logo),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (state.isUpdatingLogo) PitchGreen.copy(alpha = 0.4f) else PitchGreen,
                    modifier = Modifier.clickable(enabled = !state.isUpdatingLogo) { picker.launch() }.padding(vertical = 4.dp)
                )
                if (state.logoUrl != null) {
                    Text(" · ", color = DeepCarbon.copy(alpha = 0.3f))
                    Text(
                        text = stringResource(Res.string.btn_remove_logo),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (state.isUpdatingLogo) AlertRed.copy(alpha = 0.4f) else AlertRed,
                        modifier = Modifier.clickable(enabled = !state.isUpdatingLogo) { onRemoveLogo() }.padding(vertical = 4.dp)
                    )
                }
            }
            Text(
                text = state.logoError ?: stringResource(
                    when {
                        state.isUpdatingLogo -> Res.string.msg_logo_saving
                        state.isDirty -> Res.string.msg_unsaved_club_changes
                        else -> Res.string.msg_logo_hint
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.logoError != null) MaterialTheme.colorScheme.error else DeepCarbon.copy(alpha = 0.5f)
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
    imeAction: ImeAction = ImeAction.Next,
    // Set on the last field: its keyboard key saves, like the top bar's check.
    onSubmit: (() -> Unit)? = null,
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
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (onSubmit != null) ImeAction.Done else imeAction),
            keyboardActions = onSubmit?.let { submitKeyboardActions(it) } ?: KeyboardActions.Default,
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, disabledContainerColor = Color.White, 
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
    it.defaultSetting()
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
            onPermissionChanged = {},
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
            onPermissionChanged = {},
            onSavePermissionsClick = {}
        )
    }
}

@Preview
@Composable
private fun EditClubColoursDirtyPreview() {
    SquadifyTheme {
        val fields = sampleFields()
        val permissions = samplePermissions()
        EditClubContent(
            uiState = EditClubUiState.Idle(
                original = fields,
                form = fields.copy(theme = com.ricardofrango.squadify.model.ClubTheme("#E30613", "#FFFFFF")),
                originalPermissions = permissions,
                permissions = permissions
            ),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {},
            onPermissionChanged = { _ -> },
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
            onPermissionChanged = {},
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
                    if (it.action == ConfigurablePermission.MANAGE_MATCH) it.copy(minAgeGroupRole = null) else it
                }
            ),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {},
            onPermissionChanged = {},
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
            onPermissionChanged = {},
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
            onPermissionChanged = {},
            onSavePermissionsClick = {}
        )
    }
}

internal fun sampleMembersOverview(): ClubMembersOverview {
    val joined = Instant.parse("2025-08-01T10:00:00Z")
    fun member(id: String, first: String, last: String, role: ClubRole, roles: List<MemberAgeGroupRole> = emptyList(), linked: List<MemberLinkedPlayer> = emptyList()) =
        ClubMemberEntry(id, first, last, "${first.lowercase()}.${last.lowercase()}@gmail.com", null, role, roles, linked, joined)
    fun coach(name: String) = MemberAgeGroupRole("ag-$name", name, "2025/26", AgeGroupRole.COACH, currentSeason = true)
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
            onPermissionChanged = {},
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
