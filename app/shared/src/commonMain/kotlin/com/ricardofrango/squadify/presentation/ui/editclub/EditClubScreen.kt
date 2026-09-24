package com.ricardofrango.squadify.presentation.ui.editclub

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ricardofrango.squadify.presentation.ui.components.FMTopBar
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
import squadify.app.shared.generated.resources.ic_check
import squadify.app.shared.generated.resources.label_add_logo
import squadify.app.shared.generated.resources.label_club_address_field
import squadify.app.shared.generated.resources.label_club_name_field
import squadify.app.shared.generated.resources.label_contact_section
import squadify.app.shared.generated.resources.label_email
import squadify.app.shared.generated.resources.label_identity_section
import squadify.app.shared.generated.resources.label_phone_field
import squadify.app.shared.generated.resources.label_short_name_field
import squadify.app.shared.generated.resources.label_stadium_location_field
import squadify.app.shared.generated.resources.label_stadium_location_placeholder
import squadify.app.shared.generated.resources.label_where_section
import squadify.app.shared.generated.resources.msg_club_address_helper
import squadify.app.shared.generated.resources.msg_edit_club_details
import squadify.app.shared.generated.resources.msg_no_club_changes
import squadify.app.shared.generated.resources.msg_permissions_coming_soon
import squadify.app.shared.generated.resources.msg_short_name_helper
import squadify.app.shared.generated.resources.msg_stadium_location_helper
import squadify.app.shared.generated.resources.msg_unsaved_club_changes
import squadify.app.shared.generated.resources.tab_club_details
import squadify.app.shared.generated.resources.tab_permissions
import squadify.app.shared.generated.resources.title_club_details

private val FormRoundedShape = RoundedCornerShape(18.dp)

/**
 * Tabbed club settings hub: "Club details" (built here) and "Permissions" (an empty placeholder for
 * now -- see TECHNICAL_DECISIONS.md). Reached from Club Details' bottom "More" menu. Follows the same
 * dirty-tracked, checkmark-save-in-the-header pattern as EditPlayerScreen/AccountScreen; "Add logo" and
 * "Stadium location" mirror the design canvas artboard for this screen, with "Add logo" left
 * non-functional (no image-upload capability exists anywhere in the app yet, same reasoning as
 * Account's "Change email").
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
        onSaveClick = { viewModel.onSaveClicked(clubId, onSaved = onClubUpdated) }
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
    onSaveClick: () -> Unit
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
                EDIT_CLUB_TAB_PERMISSIONS -> PermissionsPlaceholder(boxModifier)
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
private fun PermissionsPlaceholder(modifier: Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.msg_permissions_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = DeepCarbon.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
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

@Preview
@Composable
private fun EditClubDetailsPreview() {
    SquadifyTheme {
        val fields = sampleFields()
        EditClubContent(
            uiState = EditClubUiState.Idle(original = fields, form = fields),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {}
        )
    }
}

@Preview
@Composable
private fun EditClubDetailsDirtyPreview() {
    SquadifyTheme {
        val fields = sampleFields()
        EditClubContent(
            uiState = EditClubUiState.Idle(
                original = fields,
                form = fields.copy(stadiumLocation = "Estrada da Praia, Ribamar")
            ),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {}
        )
    }
}

@Preview
@Composable
private fun EditClubPermissionsTabPreview() {
    SquadifyTheme {
        val fields = sampleFields()
        EditClubContent(
            uiState = EditClubUiState.Idle(
                selectedTab = EDIT_CLUB_TAB_PERMISSIONS,
                original = fields,
                form = fields
            ),
            onNavigateBack = {},
            onRetry = {},
            onTabSelected = {},
            onFormChange = {},
            onSaveClick = {}
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
            onSaveClick = {}
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
            onSaveClick = {}
        )
    }
}
