package com.ricardofrango.squadify.presentation.ui.createstaff

import androidx.compose.foundation.text.KeyboardActions
import com.ricardofrango.squadify.presentation.ui.components.submitKeyboardActions
import com.ricardofrango.squadify.presentation.navigation.collectSettledState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.PrimaryLoadingButton
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyClubTheme
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import squadify.app.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** One row of the batch. [key] is a stable identity for Compose iteration since rows can be removed
 * from the middle of the list. */
private data class StaffRow(
    val key: Int,
    val email: String = ""
)

private val FormRoundedShape = RoundedCornerShape(18.dp)

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private fun isValidEmail(value: String) = EMAIL_REGEX.matches(value.trim())

/**
 * Registers a brand-new person to the club, matching `AddClubMembersRequest` (a batch of emails).
 * The minimum this actually needs, per the existing invite model (`AddClubMembersUseCase`): an email
 * per row is the only field there is -- an email already tied to an account is added to the club
 * right away as a MEMBER, an unknown one gets an invite. Either way the person then shows up in
 * AddStaffToAgeGroupScreen's picker, ready to be assigned a Coach/Staff role for an age group.
 *
 * Same batch shape as CreatePlayersScreen: one card per row, "+ Add another staff member", one button
 * that submits the whole batch in a single request.
 */
@Composable
fun CreateStaffScreen(
    clubId: String,
    clubName: String,
    onNavigateBack: () -> Unit,
    onStaffCreated: () -> Unit,
    viewModel: CreateStaffViewModel = koinViewModel()
) {
    SquadifyClubTheme {
        val uiState = viewModel.state.collectSettledState()

        var nextRowKey by remember { mutableStateOf(1) }
        val rows = remember { mutableStateListOf(StaffRow(key = 0)) }
        var showValidationErrors by remember { mutableStateOf(false) }

        LaunchedEffect(viewModel.navigationEvent) {
            viewModel.navigationEvent.collect { event ->
                when (event) {
                    CreateStaffNavigationEvent.NavigateBackWithStaffCreated -> onStaffCreated()
                }
            }
        }

        CreateStaffContent(
            uiState = uiState,
            clubName = clubName,
            rows = rows,
            showValidationErrors = showValidationErrors,
            onNavigateBack = onNavigateBack,
            onEmailChange = { key, value ->
                updateRow(rows, key) { it.copy(email = value) }
                showValidationErrors = false
                viewModel.clearSubmitError()
            },
            onAddRow = {
                rows.add(StaffRow(key = nextRowKey))
                nextRowKey += 1
            },
            onRemoveRow = { key -> rows.removeAll { it.key == key } },
            onCreateClick = {
                showValidationErrors = true
                val allValid = rows.isNotEmpty() && rows.all { isValidEmail(it.email) }
                if (allValid) {
                    val emails = rows.map { it.email.trim() }.distinct()
                    viewModel.onCreateClicked(clubId, emails)
                }
            }
        )
    }
}

private inline fun updateRow(rows: MutableList<StaffRow>, key: Int, transform: (StaffRow) -> StaffRow) {
    val index = rows.indexOfFirst { it.key == key }
    if (index >= 0) rows[index] = transform(rows[index])
}

@Composable
private fun CreateStaffContent(
    uiState: CreateStaffUiState,
    clubName: String,
    rows: List<StaffRow>,
    showValidationErrors: Boolean,
    onNavigateBack: () -> Unit,
    onEmailChange: (key: Int, value: String) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (key: Int) -> Unit,
    onCreateClick: () -> Unit
) {
    val anyEmailInvalid = showValidationErrors && rows.any { !isValidEmail(it.email) }
    val selectedCount = rows.size

    Scaffold(
        containerColor = Color.White,
        topBar = {
            SquadifyTopBar(
                title = stringResource(Res.string.title_create_staff),
                subtitle = clubName,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Fixed header: intro + "add row" -- stays put as the list below grows, instead of
            // scrolling out of reach.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding.Horizontal, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.msg_staff_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.6f)
                )
                OutlinedButton(
                    onClick = onAddRow,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FormRoundedShape,
                    border = BorderStroke(1.dp, PitchGreen.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PitchGreen)
                ) {
                    Text("+ ${stringResource(Res.string.label_add_another_staff_member)}")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding.Horizontal),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rows.forEachIndexed { index, row ->
                    StaffRowCard(
                        index = index + 1,
                        row = row,
                        emailError = showValidationErrors && !isValidEmail(row.email),
                        canRemove = rows.size > 1,
                        onEmailChange = { onEmailChange(row.key, it) },
                        onRemove = { onRemoveRow(row.key) },
                        onSubmit = onCreateClick.takeIf { index == rows.lastIndex }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Fixed footer: validation summary + submit -- always reachable without scrolling.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding.Horizontal, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (anyEmailInvalid) {
                    Text(
                        text = stringResource(Res.string.msg_email_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (uiState.error != null) {
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                val label = if (selectedCount == 1) {
                    stringResource(Res.string.btn_create_staff_one)
                } else {
                    stringResource(Res.string.btn_create_staff_other, selectedCount)
                }
                PrimaryLoadingButton(
                    text = label,
                    submittingText = stringResource(Res.string.btn_creating_staff),
                    isLoading = uiState.isSubmitting,
                    onClick = onCreateClick
                )

                Text(
                    text = stringResource(Res.string.msg_staff_footer_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepCarbon.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StaffRowCard(
    index: Int,
    row: StaffRow,
    emailError: Boolean,
    canRemove: Boolean,
    onEmailChange: (String) -> Unit,
    onRemove: () -> Unit,
    // Only on the last row: its keyboard key creates them all, like the button. Earlier rows' Next
    // moves on to the following row.
    onSubmit: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FormRoundedShape,
        color = Color.White,
        border = BorderStroke(1.dp, CarbonGray.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).background(CarbonGray.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = DeepCarbon
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.label_staff_member_section).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (canRemove) {
                    Text(
                        text = "✕",
                        color = DeepCarbon.copy(alpha = 0.4f),
                        modifier = Modifier.clickable(onClick = onRemove)
                    )
                }
            }

            OutlinedTextField(
                value = row.email,
                onValueChange = onEmailChange,
                label = { Text(stringResource(Res.string.label_email_field)) },
                isError = emailError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = FormRoundedShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next),
                keyboardActions = onSubmit?.let { submitKeyboardActions(it) } ?: KeyboardActions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DeepCarbon,
                    unfocusedTextColor = DeepCarbon,
                    focusedBorderColor = PitchGreen,
                    unfocusedBorderColor = CarbonGray.copy(alpha = 0.3f),
                    focusedLabelColor = PitchGreen,
                    unfocusedLabelColor = CarbonGray
                )
            )
        }
    }
}

// ---- Previews ----

@Preview
@Composable
private fun CreateStaffContentPreview() {
    SquadifyTheme {
        CreateStaffContent(
            uiState = CreateStaffUiState(),
            clubName = "CD Ribamar",
            rows = listOf(
                StaffRow(key = 0, email = "nuno.faria@example.com"),
                StaffRow(key = 1)
            ),
            showValidationErrors = false,
            onNavigateBack = {},
            onEmailChange = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}

@Preview
@Composable
private fun CreateStaffContentErrorsPreview() {
    SquadifyTheme(darkTheme = true) {
        CreateStaffContent(
            uiState = CreateStaffUiState(error = "Something went wrong. Please try again."),
            clubName = "CD Ribamar",
            rows = listOf(StaffRow(key = 0)),
            showValidationErrors = true,
            onNavigateBack = {},
            onEmailChange = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}

@Preview(widthDp = 780, heightDp = 360)
@Composable
private fun CreateStaffContentLandscapePreview() {
    SquadifyTheme {
        CreateStaffContent(
            uiState = CreateStaffUiState(isSubmitting = true),
            clubName = "CD Ribamar",
            rows = listOf(StaffRow(key = 0, email = "nuno.faria@example.com")),
            showValidationErrors = false,
            onNavigateBack = {},
            onEmailChange = { _, _ -> },
            onAddRow = {},
            onRemoveRow = {},
            onCreateClick = {}
        )
    }
}
