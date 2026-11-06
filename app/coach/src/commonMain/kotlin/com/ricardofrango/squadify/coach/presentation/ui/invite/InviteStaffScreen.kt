package com.ricardofrango.squadify.coach.presentation.ui.invite

import com.ricardofrango.squadify.presentation.ui.components.ConfirmDialog
import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import com.ricardofrango.squadify.presentation.ui.components.previewPlans
import com.ricardofrango.squadify.presentation.ui.components.LocalPlans
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.coach.presentation.share.TextSharing
import com.ricardofrango.squadify.coach.presentation.share.rememberTextSharing
import com.ricardofrango.squadify.model.StaffInvite
import com.ricardofrango.squadify.presentation.ui.components.FormIntro
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.PinnedBottomBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.StaffNotInPlanNotice
import com.ricardofrango.squadify.model.SpacePlan
import androidx.compose.runtime.CompositionLocalProvider
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
import squadify.app.coach.generated.resources.btn_invite_send
import squadify.app.coach.generated.resources.btn_invite_sending
import squadify.app.coach.generated.resources.btn_invite_share
import squadify.app.coach.generated.resources.btn_invite_withdraw
import squadify.app.coach.generated.resources.error_invite_email_invalid
import squadify.app.coach.generated.resources.ic_copy
import squadify.app.coach.generated.resources.ic_share
import squadify.app.coach.generated.resources.invite_email_hint
import squadify.app.coach.generated.resources.invite_email_title
import squadify.app.coach.generated.resources.invite_intro
import squadify.app.coach.generated.resources.invite_none
import squadify.app.coach.generated.resources.invite_sent_to
import squadify.app.coach.generated.resources.invite_share_text
import squadify.app.coach.generated.resources.invite_valid_until
import squadify.app.coach.generated.resources.invite_waiting
import squadify.app.coach.generated.resources.label_invite_email
import squadify.app.coach.generated.resources.msg_invite_copied
import squadify.app.coach.generated.resources.msg_invite_create_failed
import squadify.app.coach.generated.resources.msg_invite_email_not_sent
import squadify.app.coach.generated.resources.msg_invite_email_sent
import squadify.app.coach.generated.resources.msg_invite_no_room
import squadify.app.coach.generated.resources.msg_invite_send_failed
import squadify.app.coach.generated.resources.msg_invite_withdraw_failed
import squadify.app.coach.generated.resources.msg_invite_withdrawn
import squadify.app.coach.generated.resources.msg_invites_failed
import squadify.app.coach.generated.resources.title_invite_withdraw
import squadify.app.coach.generated.resources.msg_invite_withdraw
import squadify.app.shared.generated.resources.btn_cancel
import squadify.app.shared.generated.resources.label_invite_staff
import kotlin.time.Instant
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * The owner's invite codes for one age group (REQUIREMENTS §5c), from Age Group Details' ⋮: create a code,
 * share it through the phone's share menu or copy it, or have one emailed (in the app's language), and
 * withdraw one not used yet. Whoever enters a code in the app joins the age group as staff.
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
                        InviteStaffMessage.NO_ROOM_ON_PLAN -> Res.string.msg_invite_no_room
                        InviteStaffMessage.EMAIL_SENT -> Res.string.msg_invite_email_sent
                        InviteStaffMessage.EMAIL_NOT_SENT -> Res.string.msg_invite_email_not_sent
                        InviteStaffMessage.SEND_FAILED -> Res.string.msg_invite_send_failed
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
        onEmailChange = viewModel::onEmailChanged,
        onSend = { viewModel.onSendClicked(ageGroupId, Locale.current.language) },
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
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onWithdraw: (code: String) -> Unit,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var confirmWithdraw by remember { mutableStateOf<StaffInvite?>(null) }
    Scaffold(
        contentWindowInsets = ScreenWindowInsets,
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
            if (loaded != null && loaded.fullOnPlan == null) {
                PinnedBottomBar {
                    PrimaryLoadingButton(
                        text = Res.string.btn_invite_create,
                        submittingText = Res.string.btn_invite_creating,
                        isLoading = loaded.isCreating,
                        onClick = onCreate,
                        enabled = !loaded.isSending && loaded.fullOnPlan == null
                    )
                }
            }
        }
    ) { paddingValues ->
        when (uiState) {
            InviteStaffUiState.Loading -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PitchGreen)
            }

            InviteStaffUiState.Failed -> ErrorScreen(title = stringResource(Res.string.msg_invites_failed), onRetry = onRetry, modifier = Modifier.fillMaxSize().padding(paddingValues))

            is InviteStaffUiState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).readableWidth(),
                contentPadding = ScreenPadding.Values,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // No room on the plan: only the plan's box, until it changes.
                uiState.fullOnPlan?.let { plan ->
                    // Only the owner invites, so it is always their own space.
                    item { StaffNotInPlanNotice(plan, ownSpace = true) }
                    return@LazyColumn
                }
                item { FormIntro(stringResource(Res.string.invite_intro, ageGroupName)) }
                item { EmailInviteCard(uiState, onEmailChange, onSend) }
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
        ConfirmDialog(
            title = stringResource(Res.string.title_invite_withdraw, invite.code),
            body = stringResource(Res.string.msg_invite_withdraw),
            onConfirm = { confirmWithdraw = null; onWithdraw(invite.code) },
            onDismiss = { confirmWithdraw = null },
            confirmLabel = stringResource(Res.string.btn_invite_withdraw),
            dismissLabel = stringResource(SharedRes.string.btn_cancel)
        )
    }
}

@Composable
private fun EmailInviteCard(state: InviteStaffUiState.Loaded, onEmailChange: (String) -> Unit, onSend: () -> Unit) {
    SectionCard(spacing = 8.dp) {
        Text(stringResource(Res.string.invite_email_title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        FormTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = stringResource(Res.string.label_invite_email),
            enabled = !state.isSending,
            keyboardType = KeyboardType.Email,
            isError = state.isEmailInvalid,
            supporting = stringResource(if (state.isEmailInvalid) Res.string.error_invite_email_invalid else Res.string.invite_email_hint),
            imeAction = ImeAction.Send,
            onImeAction = onSend
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (state.isSending) CircularProgressIndicator(color = PitchGreen, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            TextButton(onClick = onSend, enabled = state.canSend) {
                Text(
                    stringResource(if (state.isSending) Res.string.btn_invite_sending else Res.string.btn_invite_send),
                    color = if (state.canSend) PitchGreen else CarbonGray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
        invite.email?.let { Text(stringResource(Res.string.invite_sent_to, it), style = MaterialTheme.typography.bodySmall, color = CarbonGray) }
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
    StaffInvite("K7Q-4MZ", "ag-1", "Sub 11", Instant.fromEpochMilliseconds(1_790_000_000_000), Instant.fromEpochMilliseconds(1_790_604_800_000), email = "joao@example.com"),
    StaffInvite("P3X-9HD", "ag-1", "Sub 11", Instant.fromEpochMilliseconds(1_789_900_000_000), Instant.fromEpochMilliseconds(1_790_504_800_000))
)

@Composable
private fun InvitePreview(state: InviteStaffUiState) {
    SquadifyTheme {
        CompositionLocalProvider(LocalPlans provides previewPlans) {
            InviteStaffContent(
                uiState = state, ageGroupName = "Sub 11", spaceName = "Fontelas FC", snackbarHostState = remember { SnackbarHostState() },
                sharing = null, onCopied = {}, onCreate = {}, onEmailChange = {}, onSend = {}, onWithdraw = {}, onRetry = {}, onNavigateBack = {}
            )
        }
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
private fun InviteStaffInvalidEmailPreview() = InvitePreview(InviteStaffUiState.Loaded(previewInvites, email = "joao@example", isEmailInvalid = true))

@Preview
@Composable
private fun InviteStaffEmptyPreview() = InvitePreview(InviteStaffUiState.Loaded(emptyList()))

@Preview
@Composable
private fun InviteStaffFullOnFreePreview() = InvitePreview(InviteStaffUiState.Loaded(emptyList(), fullOnPlan = SpacePlan("FREE")))

@Preview
@Composable
private fun InviteStaffFullOnBasicPreview() = InvitePreview(InviteStaffUiState.Loaded(previewInvites, fullOnPlan = SpacePlan("BASIC")))
