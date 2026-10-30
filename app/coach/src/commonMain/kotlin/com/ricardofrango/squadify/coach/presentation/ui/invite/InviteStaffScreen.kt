package com.ricardofrango.squadify.coach.presentation.ui.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.coach.presentation.share.TextSharing
import com.ricardofrango.squadify.coach.presentation.share.rememberTextSharing
import com.ricardofrango.squadify.model.StaffInvite
import com.ricardofrango.squadify.presentation.ui.components.FormIntro
import com.ricardofrango.squadify.presentation.ui.components.PinnedBottomBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.btn_invite_copy
import squadify.app.coach.generated.resources.btn_invite_create
import squadify.app.coach.generated.resources.btn_invite_creating
import squadify.app.coach.generated.resources.btn_invite_share
import squadify.app.coach.generated.resources.btn_invite_withdraw
import squadify.app.coach.generated.resources.ic_copy
import squadify.app.coach.generated.resources.ic_share
import squadify.app.coach.generated.resources.invite_intro
import squadify.app.coach.generated.resources.invite_none
import squadify.app.coach.generated.resources.invite_share_text
import squadify.app.coach.generated.resources.invite_valid_until
import squadify.app.coach.generated.resources.invite_waiting
import squadify.app.coach.generated.resources.msg_invite_copied
import squadify.app.coach.generated.resources.msg_invite_create_failed
import squadify.app.coach.generated.resources.msg_invite_withdraw_failed
import squadify.app.coach.generated.resources.msg_invite_withdrawn
import squadify.app.coach.generated.resources.msg_invites_failed
import squadify.app.coach.generated.resources.title_invite_withdraw
import squadify.app.coach.generated.resources.msg_invite_withdraw
import squadify.app.shared.generated.resources.btn_cancel
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.label_invite_staff
import kotlin.time.Instant
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * The owner's invite codes for one age group (REQUIREMENTS §5c), from Age Group Details' ⋮: create a code,
 * share it through the phone's share menu or copy it, and withdraw one not used yet. Whoever enters a code
 * in the app joins the age group as staff.
 */
@Composable
fun InviteStaffScreen(
    ageGroupId: String,
    ageGroupName: String,
    spaceName: String,
    onNavigateBack: () -> Unit,
    viewModel: InviteStaffViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sharing = rememberTextSharing()

    LaunchedEffect(ageGroupId) { viewModel.load(ageGroupId) }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                getString(
                    when (message) {
                        InviteStaffMessage.CREATE_FAILED -> Res.string.msg_invite_create_failed
                        InviteStaffMessage.WITHDRAWN -> Res.string.msg_invite_withdrawn
                        InviteStaffMessage.WITHDRAW_FAILED -> Res.string.msg_invite_withdraw_failed
                    }
                )
            )
        }
    }
    val copiedText = stringResource(Res.string.msg_invite_copied)
    var copied by remember { mutableStateOf(0) }
    LaunchedEffect(copied) { if (copied > 0) snackbarHostState.showSnackbar(copiedText) }

    InviteStaffContent(
        uiState = uiState,
        ageGroupName = ageGroupName,
        spaceName = spaceName,
        snackbarHostState = snackbarHostState,
        sharing = sharing,
        onCopied = { copied++ },
        onCreate = { viewModel.onCreateClicked(ageGroupId) },
        onWithdraw = { code -> viewModel.onWithdrawConfirmed(ageGroupId, code) },
        onRetry = { viewModel.load(ageGroupId) },
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun InviteStaffContent(
    uiState: InviteStaffUiState,
    ageGroupName: String,
    spaceName: String,
    snackbarHostState: SnackbarHostState,
    sharing: TextSharing?,
    onCopied: () -> Unit,
    onCreate: () -> Unit,
    onWithdraw: (code: String) -> Unit,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var confirmWithdraw by remember { mutableStateOf<StaffInvite?>(null) }
    Scaffold(
        containerColor = ScreenBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SquadifyTopBar(
                title = stringResource(SharedRes.string.label_invite_staff),
                subtitle = "$ageGroupName · $spaceName",
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            val loaded = uiState as? InviteStaffUiState.Loaded
            if (loaded != null) {
                PinnedBottomBar {
                    PrimaryLoadingButton(
                        text = Res.string.btn_invite_create,
                        submittingText = Res.string.btn_invite_creating,
                        isLoading = loaded.isCreating,
                        onClick = onCreate
                    )
                }
            }
        }
    ) { paddingValues ->
        when (uiState) {
            InviteStaffUiState.Loading -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PitchGreen)
            }

            InviteStaffUiState.Failed -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.msg_invites_failed), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.6f))
                    TextButton(onClick = onRetry) { Text(stringResource(SharedRes.string.btn_retry), color = PitchGreen, fontWeight = FontWeight.Bold) }
                }
            }

            is InviteStaffUiState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).readableWidth(),
                contentPadding = ScreenPadding.Values,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { FormIntro(stringResource(Res.string.invite_intro, ageGroupName)) }
                item {
                    Text(
                        stringResource(Res.string.invite_waiting, uiState.invites.size).uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = DeepCarbon.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (uiState.invites.isEmpty()) {
                    item { Text(stringResource(Res.string.invite_none), style = MaterialTheme.typography.bodyMedium, color = CarbonGray) }
                }
                items(uiState.invites, key = { it.code }) { invite ->
                    val validUntil = dayMonth(invite.expiresAt)
                    val shareText = stringResource(Res.string.invite_share_text, ageGroupName, spaceName, invite.code, validUntil)
                    InviteCard(
                        invite = invite,
                        validUntil = validUntil,
                        onShare = { sharing?.share(shareText) },
                        onCopy = { sharing?.copy(invite.code); onCopied() },
                        onWithdraw = { confirmWithdraw = invite }
                    )
                }
            }
        }
    }
    confirmWithdraw?.let { invite ->
        AlertDialog(
            onDismissRequest = { confirmWithdraw = null },
            title = { Text(stringResource(Res.string.title_invite_withdraw, invite.code)) },
            text = { Text(stringResource(Res.string.msg_invite_withdraw)) },
            confirmButton = {
                TextButton(onClick = { confirmWithdraw = null; onWithdraw(invite.code) }) {
                    Text(stringResource(Res.string.btn_invite_withdraw), color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithdraw = null }) {
                    Text(stringResource(SharedRes.string.btn_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun InviteCard(invite: StaffInvite, validUntil: String, onShare: () -> Unit, onCopy: () -> Unit, onWithdraw: () -> Unit) {
    SectionCard(spacing = 4.dp) {
        Text(
            invite.code,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
            color = DeepCarbon
        )
        Text(stringResource(Res.string.invite_valid_until, validUntil), style = MaterialTheme.typography.bodySmall, color = CarbonGray)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CardAction(Res.drawable.ic_share, stringResource(Res.string.btn_invite_share), PitchGreen, onShare)
            CardAction(Res.drawable.ic_copy, stringResource(Res.string.btn_invite_copy), PitchGreen, onCopy)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onWithdraw) {
                Text(stringResource(Res.string.btn_invite_withdraw), color = AlertRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CardAction(icon: DrawableResource, label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontWeight = FontWeight.Bold)
    }
}

/** "06/11": the day a code stops working, on this phone's calendar. */
private fun dayMonth(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.day.toString().padStart(2, '0')}/${date.month.number.toString().padStart(2, '0')}"
}

private val previewInvites = listOf(
    StaffInvite("K7Q-4MZ", "ag-1", "Sub 11", Instant.fromEpochMilliseconds(1_790_000_000_000), Instant.fromEpochMilliseconds(1_790_604_800_000)),
    StaffInvite("P3X-9HD", "ag-1", "Sub 11", Instant.fromEpochMilliseconds(1_789_900_000_000), Instant.fromEpochMilliseconds(1_790_504_800_000))
)

@Composable
private fun InvitePreview(state: InviteStaffUiState) {
    SquadifyTheme {
        InviteStaffContent(
            uiState = state, ageGroupName = "Sub 11", spaceName = "Fontelas FC", snackbarHostState = remember { SnackbarHostState() },
            sharing = null, onCopied = {}, onCreate = {}, onWithdraw = {}, onRetry = {}, onNavigateBack = {}
        )
    }
}

@Preview
@Composable
private fun InviteStaffPreview() = InvitePreview(InviteStaffUiState.Loaded(previewInvites))

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun InviteStaffLandscapePreview() = InvitePreview(InviteStaffUiState.Loaded(previewInvites))

@Preview
@Composable
private fun InviteStaffEmptyPreview() = InvitePreview(InviteStaffUiState.Loaded(emptyList()))
