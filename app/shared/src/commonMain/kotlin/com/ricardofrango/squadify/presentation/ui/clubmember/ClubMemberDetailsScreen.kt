package com.ricardofrango.squadify.presentation.ui.clubmember

import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.ClubMemberEntry
import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.MemberAgeGroupRole
import com.ricardofrango.squadify.model.MemberLinkedPlayer
import com.ricardofrango.squadify.model.PendingClubInvite
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UserPlayerRelationship
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsMessage
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.components.shortDateLabel
import com.ricardofrango.squadify.presentation.ui.editclub.MemberAvatar
import com.ricardofrango.squadify.presentation.ui.editclub.RoleOption
import com.ricardofrango.squadify.presentation.ui.editclub.initials
import com.ricardofrango.squadify.presentation.ui.editclub.labelRes
import com.ricardofrango.squadify.presentation.ui.editclub.sampleMembersOverview
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.WhiteSmoke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.btn_cancel
import squadify.app.shared.generated.resources.ic_chevron_right
import squadify.app.shared.generated.resources.ic_document
import squadify.app.shared.generated.resources.member_account_created
import squadify.app.shared.generated.resources.member_choose_role
import squadify.app.shared.generated.resources.member_contact
import squadify.app.shared.generated.resources.member_copy
import squadify.app.shared.generated.resources.member_current
import squadify.app.shared.generated.resources.member_email_copied
import squadify.app.shared.generated.resources.member_empty_body
import squadify.app.shared.generated.resources.member_empty_title
import squadify.app.shared.generated.resources.member_in_club
import squadify.app.shared.generated.resources.member_invite_heading
import squadify.app.shared.generated.resources.member_invite_note
import squadify.app.shared.generated.resources.member_invite_pending
import squadify.app.shared.generated.resources.member_invited_ago
import squadify.app.shared.generated.resources.member_invited_today
import squadify.app.shared.generated.resources.member_load_failed_body
import squadify.app.shared.generated.resources.member_load_failed_title
import squadify.app.shared.generated.resources.member_lock_admin_only
import squadify.app.shared.generated.resources.member_lock_last_admin
import squadify.app.shared.generated.resources.member_make_admin_body
import squadify.app.shared.generated.resources.member_make_admin_confirm
import squadify.app.shared.generated.resources.member_make_admin_title
import squadify.app.shared.generated.resources.member_make_role
import squadify.app.shared.generated.resources.member_no_phone
import squadify.app.shared.generated.resources.member_phone_copied
import squadify.app.shared.generated.resources.member_rel_father
import squadify.app.shared.generated.resources.member_rel_guardian
import squadify.app.shared.generated.resources.member_rel_mother
import squadify.app.shared.generated.resources.member_rel_other
import squadify.app.shared.generated.resources.member_remove
import squadify.app.shared.generated.resources.member_remove_body_account
import squadify.app.shared.generated.resources.member_remove_body_links
import squadify.app.shared.generated.resources.member_remove_body_role
import squadify.app.shared.generated.resources.member_remove_body_roles
import squadify.app.shared.generated.resources.member_remove_confirm
import squadify.app.shared.generated.resources.member_remove_title
import squadify.app.shared.generated.resources.member_resend_invite
import squadify.app.shared.generated.resources.member_role_coach
import squadify.app.shared.generated.resources.member_role_staff
import squadify.app.shared.generated.resources.member_saving
import squadify.app.shared.generated.resources.member_title
import squadify.app.shared.generated.resources.member_will_join_as
import squadify.app.shared.generated.resources.members_cancel_invite
import squadify.app.shared.generated.resources.members_club_role
import squadify.app.shared.generated.resources.members_you
import kotlin.time.Clock

/**
 * Club member details (design 35), opened from Club settings → Members (and from staff lists):
 * contact (tap to mail or call, copy), the club role with 34's rules, and what they do in the club --
 * age-group roles open the age group, linked players open Player details. A pending invite shows only
 * what exists: the email, the intended role and its age, with Resend and Cancel invite.
 * "Remove from club" ships later.
 */
@Composable
fun ClubMemberDetailsScreen(
    clubId: String,
    userId: String?,
    inviteId: String?,
    onNavigateBack: () -> Unit,
    // The members list underneath is stale (a role changed); [message] is shown there when set.
    onMembersChanged: (message: String?) -> Unit,
    // You demoted yourself: back to Club details, past Club settings you may no longer reach.
    onReturnToClub: () -> Unit,
    onOpenAgeGroup: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    viewModel: ClubMemberDetailsViewModel = koinViewModel()
) {
    val state = viewModel.state.collectSettledState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(clubId, userId, inviteId) { viewModel.load(clubId, userId, inviteId) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ClubMemberDetailsEvent.Message -> {
                    onMembersChanged(null)
                    launch { snackbarHostState.showSnackbar(event.text) }
                }
                is ClubMemberDetailsEvent.Closed -> {
                    onMembersChanged(event.text)
                    onNavigateBack()
                }
                is ClubMemberDetailsEvent.LeftSettings -> {
                    launch { snackbarHostState.showSnackbar(event.text) }
                    delay(1500)
                    onReturnToClub()
                }
            }
        }
    }

    ClubMemberDetailsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRetry = { viewModel.load(clubId, userId, inviteId, force = true) },
        onSelectRole = viewModel::selectRole,
        onSaveRole = { viewModel.saveRole(clubId) },
        onResend = { viewModel.resendInvite(clubId) },
        onCancelInvite = { viewModel.cancelInvite(clubId) },
        onRemove = { viewModel.removeMember(clubId) },
        onOpenAgeGroup = onOpenAgeGroup,
        onOpenPlayer = onOpenPlayer
    )
}

@Composable
private fun ClubMemberDetailsContent(
    state: ClubMemberDetailsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectRole: (ClubRole) -> Unit,
    onSaveRole: () -> Unit,
    onResend: () -> Unit,
    onCancelInvite: () -> Unit,
    onOpenAgeGroup: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onRemove: () -> Unit = {}
) {
    var confirmAdmin by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = WhiteSmoke,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(Modifier.background(CarbonGray)) {
                FMTopBar(title = stringResource(Res.string.member_title), onNavigateBack = onNavigateBack)
                when (state) {
                    is ClubMemberDetailsUiState.Member -> MemberHeader(state)
                    is ClubMemberDetailsUiState.Invite -> InviteHeader(state.invite)
                    else -> {}
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
            when (state) {
                ClubMemberDetailsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen)
                }

                ClubMemberDetailsUiState.Error -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    StatsCard(contentPadding = 16.dp) {
                        StatsMessage(stringResource(Res.string.member_load_failed_title), stringResource(Res.string.member_load_failed_body), onRetry = onRetry)
                    }
                }

                is ClubMemberDetailsUiState.Member -> {
                    val contact: @Composable ColumnScope.() -> Unit = {
                        ContactCard(state.member, snackbarHostState)
                        RoleCard(state, onSelectRole, onSave = {
                            if (state.selectedRole == ClubRole.CLUB_ADMIN) confirmAdmin = true else onSaveRole()
                        })
                    }
                    val inClub: @Composable ColumnScope.() -> Unit = {
                        InClubCard(state.member, onOpenAgeGroup, onOpenPlayer)
                        // A red row at the bottom rather than a ⋮ item: the confirmation does the protecting.
                        if (canRemove(state.overview, state.member)) RemoveRow(enabled = !state.saving) { confirmRemove = true }
                    }
                    if (landscape) {
                        // Contact and role on the left, In the club on the right (35e).
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = contact)
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = inClub)
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            contact()
                            inClub()
                        }
                    }
                }

                is ClubMemberDetailsUiState.Invite -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InviteCard(state, onResend, onCancelInvite)
                }
            }
        }
    }

    val member = state as? ClubMemberDetailsUiState.Member
    if (confirmRemove && member != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = Color.White,
            titleContentColor = DeepCarbon,
            textContentColor = DeepCarbon.copy(alpha = 0.75f),
            title = { Text(stringResource(Res.string.member_remove_title, member.member.fullName())) },
            text = { Text(removeConsequences(member.member)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text(stringResource(Res.string.member_remove_confirm), color = AlertRed) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(Res.string.btn_cancel), color = CarbonGray) } }
        )
    }
    if (confirmAdmin && member != null) {
        AlertDialog(
            onDismissRequest = { confirmAdmin = false },
            containerColor = Color.White,
            titleContentColor = DeepCarbon,
            textContentColor = DeepCarbon.copy(alpha = 0.75f),
            title = { Text(stringResource(Res.string.member_make_admin_title, member.member.fullName())) },
            text = { Text(stringResource(Res.string.member_make_admin_body)) },
            confirmButton = { TextButton(onClick = { confirmAdmin = false; onSaveRole() }) { Text(stringResource(Res.string.member_make_admin_confirm), color = PitchGreen) } },
            dismissButton = { TextButton(onClick = { confirmAdmin = false }) { Text(stringResource(Res.string.btn_cancel), color = CarbonGray) } }
        )
    }
}

private fun ClubMemberEntry.fullName() = "$firstName $lastName"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderBand(initials: String, title: String, chip: String, line: String) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(60.dp).background(PitchGreen.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = PitchGreen)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            // Wraps, so a long "Invited 12 days ago" drops under the chip instead of being cut off.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = PitchGreen) {
                    Text(chip, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun MemberHeader(state: ClubMemberDetailsUiState.Member) {
    val m = state.member
    val created = m.accountCreatedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    HeaderBand(
        initials = m.initials(),
        title = if (state.isYou) "${m.fullName()} ${stringResource(Res.string.members_you)}" else m.fullName(),
        chip = stringResource(m.role.labelRes()),
        line = stringResource(Res.string.member_account_created, "${shortDateLabel(created)} ${created.year}")
    )
}

@Composable
private fun InviteHeader(invite: PendingClubInvite) {
    HeaderBand(initials = "@", title = invite.email, chip = stringResource(Res.string.member_invite_pending), line = invitedAgo(invite))
}

@Composable
private fun invitedAgo(invite: PendingClubInvite): String {
    val days = (Clock.System.now() - invite.invitedAt).inWholeDays.toInt().coerceAtLeast(0)
    return if (days == 0) stringResource(Res.string.member_invited_today) else pluralStringResource(Res.plurals.member_invited_ago, days, days)
}

// ---- Contact ----

@Composable
@Suppress("DEPRECATION") // LocalClipboard's ClipEntry has no common constructor yet; the text manager works on every target.
private fun ContactCard(member: ClubMemberEntry, snackbarHostState: SnackbarHostState) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copy: (String, org.jetbrains.compose.resources.StringResource) -> Unit = { text, toast ->
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbarHostState.showSnackbar(getString(toast)) }
    }
    StatsCard(contentPadding = 0.dp) {
        StatsHeading(stringResource(Res.string.member_contact), Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp))
        ContactRow(member.email, onOpen = { runCatching { uriHandler.openUri("mailto:${member.email}") } }, onCopy = { copy(member.email, Res.string.member_email_copied) })
        HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
        val phone = member.phone
        if (phone != null) {
            ContactRow(phone, onOpen = { runCatching { uriHandler.openUri("tel:${phone.filter { it.isDigit() || it == '+' }}") } }, onCopy = { copy(phone, Res.string.member_phone_copied) })
        } else {
            // Shown rather than hidden, so admins know it's missing.
            Text(stringResource(Res.string.member_no_phone), Modifier.padding(horizontal = 14.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyMedium, color = StatsSecondaryText)
        }
    }
}

@Composable
private fun ContactRow(text: String, onOpen: () -> Unit, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).padding(vertical = 14.dp), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp), color = PitchGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onCopy), contentAlignment = Alignment.Center) {
            Icon(painterResource(Res.drawable.ic_document), contentDescription = stringResource(Res.string.member_copy), tint = DeepCarbon.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
        }
    }
}

// ---- Club role ----

@Composable
private fun RoleCard(state: ClubMemberDetailsUiState.Member, onSelect: (ClubRole) -> Unit, onSave: () -> Unit) {
    val member = state.member
    StatsCard(contentPadding = 14.dp) {
        StatsHeading(stringResource(Res.string.members_club_role), Modifier.padding(bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ClubRole.CLUB_ADMIN, ClubRole.COORDINATOR, ClubRole.MEMBER).forEach { option ->
                val lock = roleLock(state.overview, member, option)
                Box {
                    RoleOption(
                        role = option,
                        selected = option == state.selectedRole,
                        enabled = lock == null && !state.saving,
                        reason = null,
                        onClick = { onSelect(option) }
                    )
                    if (option == member.role) {
                        Text(
                            stringResource(Res.string.member_current),
                            Modifier.align(Alignment.TopEnd).padding(10.dp).background(CarbonGray.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = DeepCarbon
                        )
                    }
                }
            }
        }
        // The reason is said once, under the options, rather than on each locked one -- and when nothing
        // else can be picked it replaces the button, which could never be enabled (35a).
        val options = listOf(ClubRole.CLUB_ADMIN, ClubRole.COORDINATOR, ClubRole.MEMBER).filter { it != member.role }
        val locks = options.mapNotNull { roleLock(state.overview, member, it) }.distinct()
        locks.forEach { lock ->
            Spacer(Modifier.height(10.dp))
            Text(stringResource(lock.messageRes()), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = AmberText)
        }
        if (options.all { roleLock(state.overview, member, it) != null }) return@StatsCard
        Spacer(Modifier.height(12.dp))
        val changed = state.selectedRole != member.role
        PrimaryLoadingButton(
            text = if (changed) stringResource(Res.string.member_make_role, stringResource(state.selectedRole.labelRes())) else stringResource(Res.string.member_choose_role),
            submittingText = stringResource(Res.string.member_saving),
            isLoading = state.saving,
            enabled = state.canSave,
            onClick = onSave
        )
    }
}

private fun RoleLock.messageRes() = when (this) {
    RoleLock.LAST_ADMIN -> Res.string.member_lock_last_admin
    RoleLock.ADMIN_ONLY -> Res.string.member_lock_admin_only
}

// ---- In the club ----

@Composable
private fun InClubCard(member: ClubMemberEntry, onOpenAgeGroup: (String) -> Unit, onOpenPlayer: (String) -> Unit) {
    StatsCard(contentPadding = 0.dp) {
        StatsHeading(stringResource(Res.string.member_in_club), Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp))
        if (member.ageGroupRoles.isEmpty() && member.linkedPlayers.isEmpty()) {
            StatsMessage(
                stringResource(Res.string.member_empty_title),
                stringResource(Res.string.member_empty_body, member.firstName),
                Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp)
            )
            return@StatsCard
        }
        // What they do before who they're family to -- this is reached from club admin.
        var first = true
        member.ageGroupRoles.forEach { role ->
            if (!first) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
            first = false
            val roleName = stringResource(if (role.role == TeamRole.COACH) Res.string.member_role_coach else Res.string.member_role_staff)
            LinkRow("$roleName · ${role.ageGroupName} · ${role.seasonName}", meta = null) { onOpenAgeGroup(role.ageGroupId) }
        }
        member.linkedPlayers.forEach { link ->
            if (!first) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
            first = false
            val meta = listOfNotNull(link.ageGroupName, link.shirtNumber?.let { "#$it" }).joinToString(" · ").ifEmpty { null }
            LinkRow(stringResource(link.relationship.labelRes(), link.name), meta) { onOpenPlayer(link.playerId) }
        }
    }
}

private fun UserPlayerRelationship.labelRes() = when (this) {
    UserPlayerRelationship.FATHER -> Res.string.member_rel_father
    UserPlayerRelationship.MOTHER -> Res.string.member_rel_mother
    UserPlayerRelationship.GUARDIAN -> Res.string.member_rel_guardian
    UserPlayerRelationship.OTHER -> Res.string.member_rel_other
}

@Composable
private fun LinkRow(text: String, meta: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp), color = DeepCarbon)
            if (meta != null) Text(meta, style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
        }
        Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null, tint = DeepCarbon.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
    }
}

// ---- Remove from club ----

/**
 * What removing takes away, spelled out: the club role and the staff roles in the club's age groups.
 * Parent links stay -- being a player's parent doesn't depend on a club role -- and the dialog says so.
 */
@Composable
private fun removeConsequences(member: ClubMemberEntry): String {
    val role = stringResource(member.role.labelRes())
    val ageGroups = member.ageGroupRoles.map { it.ageGroupName }.distinct()
    return buildList {
        add(
            if (ageGroups.isEmpty()) stringResource(Res.string.member_remove_body_role, role)
            else stringResource(Res.string.member_remove_body_roles, role, ageGroups.joinToString(", "))
        )
        if (member.linkedPlayers.isNotEmpty()) add(stringResource(Res.string.member_remove_body_links, member.linkedPlayers.joinToString(", ") { it.name }))
        add(stringResource(Res.string.member_remove_body_account))
    }.joinToString(" ")
}

@Composable
private fun RemoveRow(enabled: Boolean, onClick: () -> Unit) {
    StatsCard(contentPadding = 0.dp) {
        Text(
            stringResource(Res.string.member_remove),
            Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 15.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = if (enabled) AlertRed else AlertRed.copy(alpha = 0.4f)
        )
    }
}

// ---- Pending invite ----

@Composable
private fun InviteCard(state: ClubMemberDetailsUiState.Invite, onResend: () -> Unit, onCancel: () -> Unit) {
    val invite = state.invite
    StatsCard(contentPadding = 14.dp) {
        StatsHeading(stringResource(Res.string.member_invite_heading), Modifier.padding(bottom = 10.dp))
        Text(invite.email, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.member_will_join_as), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
        Text(stringResource(invite.role.labelRes()), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.member_invite_note, invite.email), style = MaterialTheme.typography.bodySmall, color = StatsSecondaryText)
        Spacer(Modifier.height(16.dp))
        PrimaryLoadingButton(
            text = stringResource(Res.string.member_resend_invite),
            submittingText = stringResource(Res.string.member_resend_invite),
            isLoading = state.busy,
            enabled = !state.busy,
            onClick = onResend
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = onCancel,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, AlertRed.copy(alpha = 0.4f))
        ) {
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.members_cancel_invite), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = AlertRed)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Composable
private fun PreviewFrame(state: ClubMemberDetailsUiState) {
    SquadifyTheme {
        ClubMemberDetailsContent(
            state = state, snackbarHostState = remember { SnackbarHostState() }, onNavigateBack = {}, onRetry = {},
            onSelectRole = {}, onSaveRole = {}, onResend = {}, onCancelInvite = {}, onOpenAgeGroup = {}, onOpenPlayer = {}
        )
    }
}

private fun richMember(overview: ClubMembersOverview) = overview.members.first { it.userId == "u3" }.copy(
    phone = "+351 913 222 401",
    ageGroupRoles = listOf(
        MemberAgeGroupRole("ag15", "Sub-15", "26/27", TeamRole.COACH, currentSeason = true),
        MemberAgeGroupRole("ag11", "Sub-11", "26/27", TeamRole.STAFF, currentSeason = true)
    ),
    linkedPlayers = listOf(MemberLinkedPlayer("p2", "Duarte Vilaça", UserPlayerRelationship.MOTHER, "Sub-15", 2))
)

@Preview
@Composable
private fun ClubMemberDetailsPreview() {
    val overview = sampleMembersOverview()
    PreviewFrame(ClubMemberDetailsUiState.Member(overview, richMember(overview), selectedRole = ClubRole.MEMBER))
}

@Preview
@Composable
private fun ClubMemberDetailsLastAdminPreview() {
    val overview = sampleMembersOverview()
    PreviewFrame(ClubMemberDetailsUiState.Member(overview, overview.members.first()))
}

@Preview
@Composable
private fun ClubMemberDetailsCoordinatorViewPreview() {
    val overview = sampleMembersOverview().copy(viewerRole = ClubRole.COORDINATOR, viewerUserId = "u2")
    PreviewFrame(ClubMemberDetailsUiState.Member(overview, overview.members.first { it.userId == "u2" }))
}

@Preview
@Composable
private fun ClubMemberDetailsInvitePreview() {
    val overview = sampleMembersOverview()
    PreviewFrame(ClubMemberDetailsUiState.Invite(overview, overview.pendingInvites.first()))
}

@Preview
@Composable
private fun ClubMemberDetailsErrorPreview() = PreviewFrame(ClubMemberDetailsUiState.Error)
