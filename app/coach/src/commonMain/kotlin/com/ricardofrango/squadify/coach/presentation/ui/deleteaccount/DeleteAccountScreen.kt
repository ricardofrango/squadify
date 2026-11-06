package com.ricardofrango.squadify.coach.presentation.ui.deleteaccount

import com.ricardofrango.squadify.presentation.ui.components.ConfirmDialog
import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.AccountDeletionSummary
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.PinnedBottomBar
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.SignedOutAlert
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.btn_delete_account_confirm
import squadify.app.coach.generated.resources.btn_delete_account_deleting
import squadify.app.coach.generated.resources.delete_account_age_groups
import squadify.app.coach.generated.resources.delete_account_final
import squadify.app.coach.generated.resources.delete_account_games
import squadify.app.coach.generated.resources.delete_account_password
import squadify.app.coach.generated.resources.delete_account_players
import squadify.app.coach.generated.resources.delete_account_space
import squadify.app.coach.generated.resources.delete_account_staff
import squadify.app.coach.generated.resources.delete_account_staff_spaces
import squadify.app.coach.generated.resources.delete_account_title
import squadify.app.coach.generated.resources.msg_delete_account_failed
import squadify.app.coach.generated.resources.msg_delete_account_load_failed
import squadify.app.coach.generated.resources.msg_delete_account_wrong_password
import squadify.app.coach.generated.resources.title_delete_account_confirm
import squadify.app.shared.generated.resources.btn_cancel
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * Deleting the coach's account (REQUIREMENTS §5c; the stores require it), from Account: what goes with it
 * (their own space with everything in it, and who loses access), their password, and a last confirmation.
 * It's deleted at once and for good, then they're signed out.
 */
@Composable
fun DeleteAccountScreen(
    onNavigateBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DeleteAccountViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDeleted() } }

    if ((uiState as? DeleteAccountUiState.Idle)?.signedOut == true) {
        SignedOutAlert(onConfirm = viewModel::finishSignOut)
    }

    DeleteAccountContent(
        uiState = uiState,
        onPasswordChanged = viewModel::onPasswordChanged,
        onDelete = viewModel::onDeleteConfirmed,
        onRetry = viewModel::load,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun DeleteAccountContent(
    uiState: DeleteAccountUiState,
    onPasswordChanged: (String) -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var confirm by remember { mutableStateOf(false) }
    val idle = uiState as? DeleteAccountUiState.Idle
    Scaffold(
        contentWindowInsets = ScreenWindowInsets,
        containerColor = ScreenBackground,
        topBar = { SquadifyTopBar(title = stringResource(Res.string.delete_account_title), onNavigateBack = onNavigateBack) },
        bottomBar = {
            if (idle != null) {
                PinnedBottomBar {
                    FormErrorLine(
                        when (idle.error) {
                            DeleteAccountError.WRONG_PASSWORD -> stringResource(Res.string.msg_delete_account_wrong_password)
                            DeleteAccountError.FAILED -> stringResource(Res.string.msg_delete_account_failed)
                            null -> null
                        }
                    )
                    Button(
                        onClick = { confirm = true },
                        enabled = idle.canDelete,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                    ) {
                        Text(
                            stringResource(if (idle.isDeleting) Res.string.btn_delete_account_deleting else Res.string.btn_delete_account_confirm),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        when (uiState) {
            DeleteAccountUiState.Loading -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PitchGreen)
            }

            DeleteAccountUiState.Failed -> ErrorScreen(title = stringResource(Res.string.msg_delete_account_load_failed), onRetry = onRetry, modifier = Modifier.fillMaxSize().padding(paddingValues))

            is DeleteAccountUiState.Idle -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .readableWidth()
                    .screenContentPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionCard(spacing = 10.dp) {
                    WhatGoes(uiState.summary)
                    Text(stringResource(Res.string.delete_account_final), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = AlertRed)
                }
                FormTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChanged,
                    label = stringResource(Res.string.delete_account_password),
                    enabled = !uiState.isDeleting,
                    isError = uiState.error == DeleteAccountError.WRONG_PASSWORD,
                    keyboardType = KeyboardType.Password,
                    hidden = true,
                    imeAction = ImeAction.Done,
                    onImeAction = { if (uiState.canDelete) confirm = true }
                )
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(Res.string.title_delete_account_confirm),
            body = stringResource(Res.string.delete_account_final),
            onConfirm = { confirm = false; onDelete() },
            onDismiss = { confirm = false },
            confirmLabel = stringResource(Res.string.btn_delete_account_confirm),
            dismissLabel = stringResource(SharedRes.string.btn_cancel)
        )
    }
}

/** Their own space and what's in it, if they own one, and that they leave the spaces they joined. */
@Composable
private fun WhatGoes(summary: AccountDeletionSummary) {
    val body = MaterialTheme.typography.bodyMedium
    summary.ownSpaceName?.let { name ->
        Text(
            stringResource(
                Res.string.delete_account_space,
                name,
                pluralStringResource(Res.plurals.delete_account_age_groups, summary.ageGroups, summary.ageGroups),
                pluralStringResource(Res.plurals.delete_account_players, summary.players, summary.players),
                pluralStringResource(Res.plurals.delete_account_games, summary.matches, summary.matches)
            ),
            style = body, color = DeepCarbon
        )
        if (summary.staff > 0) {
            Text(pluralStringResource(Res.plurals.delete_account_staff, summary.staff, summary.staff), style = body, color = DeepCarbon)
        }
    }
    Text(stringResource(Res.string.delete_account_staff_spaces), style = body, color = DeepCarbon)
}

private val previewOwner = AccountDeletionSummary("Fontelas FC", ageGroups = 2, players = 18, matches = 7, staff = 1)

@Composable
private fun DeleteAccountPreviewContent(state: DeleteAccountUiState) {
    SquadifyTheme {
        DeleteAccountContent(uiState = state, onPasswordChanged = {}, onDelete = {}, onRetry = {}, onNavigateBack = {})
    }
}

@Preview
@Composable
private fun DeleteAccountOwnerPreview() = DeleteAccountPreviewContent(DeleteAccountUiState.Idle(previewOwner, password = "secret"))

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun DeleteAccountOwnerLandscapePreview() = DeleteAccountPreviewContent(DeleteAccountUiState.Idle(previewOwner, password = "secret"))

@Preview
@Composable
private fun DeleteAccountStaffPreview() =
    DeleteAccountPreviewContent(DeleteAccountUiState.Idle(AccountDeletionSummary(), error = DeleteAccountError.WRONG_PASSWORD))
