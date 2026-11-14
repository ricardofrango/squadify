package com.ricardofrango.squadify.coach.presentation.ui.space

import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import androidx.compose.runtime.CompositionLocalProvider
import com.ricardofrango.squadify.presentation.ui.components.previewPlans
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.ricardofrango.squadify.presentation.ui.components.LocalPlans
import com.ricardofrango.squadify.presentation.ui.components.displayName
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.presentation.ui.theme.AmberText
import com.ricardofrango.squadify.presentation.ui.theme.DeepGreen
import com.ricardofrango.squadify.presentation.ui.theme.SectionBorderColor
import squadify.app.coach.generated.resources.btn_see_plans_row
import squadify.app.coach.generated.resources.plan_age_groups_no_limit
import squadify.app.coach.generated.resources.plan_age_groups_of
import squadify.app.coach.generated.resources.plan_card_this_season
import squadify.app.coach.generated.resources.plan_card_title
import squadify.app.coach.generated.resources.plan_people_just_you
import squadify.app.coach.generated.resources.plan_people_no_limit
import squadify.app.coach.generated.resources.plan_people_up_to
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.components.ClubLogo
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.DialogButtons
import com.ricardofrango.squadify.presentation.ui.components.FormErrorLine
import com.ricardofrango.squadify.presentation.ui.components.FormTextField
import com.ricardofrango.squadify.presentation.ui.components.SquadifyDialog
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.resources.pluralStringResource
import squadify.app.coach.generated.resources.btn_delete_space
import squadify.app.coach.generated.resources.delete_account_age_groups
import squadify.app.coach.generated.resources.delete_account_password
import squadify.app.coach.generated.resources.delete_space_body
import squadify.app.coach.generated.resources.delete_space_plan
import squadify.app.coach.generated.resources.delete_space_title
import squadify.app.coach.generated.resources.msg_delete_space_failed
import squadify.app.coach.generated.resources.msg_delete_space_wrong_password
import squadify.app.coach.generated.resources.btn_delete_account_deleting
import squadify.app.coach.generated.resources.btn_leave_space
import squadify.app.coach.generated.resources.leave_space_body
import squadify.app.coach.generated.resources.leave_space_title
import squadify.app.coach.generated.resources.msg_leave_space_failed
import squadify.app.coach.generated.resources.msg_leave_space_wrong_password
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.msg_my_space_failed
import squadify.app.coach.generated.resources.my_space_title
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.sport_football
import squadify.app.shared.generated.resources.sport_futsal
import kotlin.time.Instant
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * A space's edit screen: its name and sport (a space has no logo: it's shown with the Squadify mark). The owner sees
 * the Plan card and deleting the space; staff see only the header and "Leave space". [spaceId] null is the owner's own.
 */
@Composable
fun MySpaceScreen(
    onNavigateBack: () -> Unit,
    onSeePlans: () -> Unit,
    onSpaceDeleted: () -> Unit,
    spaceId: String? = null,
    onSpaceLeft: () -> Unit = {},
    viewModel: MySpaceViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val deleteDialog by viewModel.deleteDialog.collectAsStateWithLifecycle()
    val leaveDialog by viewModel.leaveDialog.collectAsStateWithLifecycle()
    LaunchedEffect(spaceId) { viewModel.open(spaceId) }
    LaunchedEffect(viewModel) { viewModel.spaceDeleted.collect { onSpaceDeleted() } }
    LaunchedEffect(viewModel) { viewModel.spaceLeft.collect { onSpaceLeft() } }
    MySpaceContent(
        uiState = uiState,
        onRetry = viewModel::load,
        onSeePlans = onSeePlans,
        onNavigateBack = onNavigateBack,
        deleteDialog = deleteDialog,
        onOpenDeleteSpace = viewModel::onDeleteSpaceOpened,
        onDismissDeleteSpace = viewModel::onDeleteSpaceDismissed,
        onDeletePasswordChanged = viewModel::onDeletePasswordChanged,
        onConfirmDeleteSpace = viewModel::onDeleteSpaceConfirmed,
        leaveDialog = leaveDialog,
        onOpenLeaveSpace = viewModel::onLeaveSpaceOpened,
        onDismissLeaveSpace = viewModel::onLeaveSpaceDismissed,
        onLeavePasswordChanged = viewModel::onLeavePasswordChanged,
        onConfirmLeaveSpace = viewModel::onLeaveSpaceConfirmed
    )
}

@Composable
private fun MySpaceContent(
    uiState: MySpaceUiState,
    onRetry: () -> Unit,
    onSeePlans: () -> Unit,
    onNavigateBack: () -> Unit,
    deleteDialog: DeleteSpaceDialog? = null,
    onOpenDeleteSpace: () -> Unit = {},
    onDismissDeleteSpace: () -> Unit = {},
    onDeletePasswordChanged: (String) -> Unit = {},
    onConfirmDeleteSpace: () -> Unit = {},
    leaveDialog: LeaveSpaceDialog? = null,
    onOpenLeaveSpace: () -> Unit = {},
    onDismissLeaveSpace: () -> Unit = {},
    onLeavePasswordChanged: (String) -> Unit = {},
    onConfirmLeaveSpace: () -> Unit = {}
) {
    Scaffold(
        contentWindowInsets = ScreenWindowInsets,
        containerColor = ScreenBackground,
        topBar = { SquadifyTopBar(title = stringResource(Res.string.my_space_title), onNavigateBack = onNavigateBack) }
    ) { paddingValues ->
        when (uiState) {
            MySpaceUiState.Loading -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PitchGreen)
            }
            MySpaceUiState.Failed -> ErrorScreen(title = stringResource(Res.string.msg_my_space_failed), body = null, onRetry = onRetry, modifier = Modifier.fillMaxSize().padding(paddingValues))
            is MySpaceUiState.Loaded -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .readableWidth()
                    .screenContentPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionCard { SpaceHeader(uiState) }
                if (uiState.isOwner) {
                    PlanCard(uiState.plan, uiState.ageGroupsThisSeason, onSeePlans = onSeePlans)
                    // Deleting the space takes its age groups, players, games and staff with it.
                    TextButton(onClick = onOpenDeleteSpace, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.btn_delete_space), color = AlertRed, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Staff leave the space: their access to its age groups goes with it, their account stays.
                    TextButton(onClick = onOpenLeaveSpace, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.btn_leave_space), color = AlertRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (uiState is MySpaceUiState.Loaded && uiState.isOwner && deleteDialog != null) {
        DeleteSpaceConfirmation(
            loaded = uiState,
            dialog = deleteDialog,
            onPasswordChanged = onDeletePasswordChanged,
            onConfirm = onConfirmDeleteSpace,
            onDismiss = onDismissDeleteSpace
        )
    }
    if (uiState is MySpaceUiState.Loaded && !uiState.isOwner && leaveDialog != null) {
        LeaveSpaceConfirmation(
            dialog = leaveDialog,
            onPasswordChanged = onLeavePasswordChanged,
            onConfirm = onConfirmLeaveSpace,
            onDismiss = onDismissLeaveSpace
        )
    }
}

/** A staff member's password to leave the space: they lose its age groups, and nothing else of theirs goes. */
@Composable
private fun LeaveSpaceConfirmation(
    dialog: LeaveSpaceDialog,
    onPasswordChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    SquadifyDialog(
        title = stringResource(Res.string.leave_space_title),
        body = stringResource(Res.string.leave_space_body),
        onDismiss = onDismiss,
        content = {
            Spacer(Modifier.height(12.dp))
            FormTextField(
                value = dialog.password,
                onValueChange = onPasswordChanged,
                label = stringResource(Res.string.delete_account_password),
                enabled = !dialog.isLeaving,
                isError = dialog.error == LeaveSpaceError.WRONG_PASSWORD,
                keyboardType = KeyboardType.Password,
                hidden = true,
                imeAction = ImeAction.Done,
                onImeAction = { if (dialog.canLeave) onConfirm() }
            )
            FormErrorLine(
                when (dialog.error) {
                    LeaveSpaceError.WRONG_PASSWORD -> stringResource(Res.string.msg_leave_space_wrong_password)
                    LeaveSpaceError.FAILED -> stringResource(Res.string.msg_leave_space_failed)
                    null -> null
                }
            )
        },
        buttons = {
            DialogButtons(
                confirmLabel = stringResource(Res.string.btn_leave_space),
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                destructive = true,
                confirmEnabled = dialog.canLeave
            )
        }
    )
}

/** The owner's password, and what goes with the space; the space is deleted at once and for good. */
@Composable
private fun DeleteSpaceConfirmation(
    loaded: MySpaceUiState.Loaded,
    dialog: DeleteSpaceDialog,
    onPasswordChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    SquadifyDialog(
        title = stringResource(Res.string.delete_space_title),
        body = stringResource(Res.string.delete_space_body),
        onDismiss = onDismiss,
        content = {
            if (loaded.ageGroupCount > 0) {
                Text(
                    pluralStringResource(Res.plurals.delete_account_age_groups, loaded.ageGroupCount, loaded.ageGroupCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeepCarbon
                )
            }
            // Deleting doesn't end a paid plan: the store's subscription has to be cancelled there.
            if (loaded.plan.id != "FREE") {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.delete_space_plan), style = MaterialTheme.typography.bodySmall, color = CarbonGray)
            }
            Spacer(Modifier.height(12.dp))
            FormTextField(
                value = dialog.password,
                onValueChange = onPasswordChanged,
                label = stringResource(Res.string.delete_account_password),
                enabled = !dialog.isDeleting,
                isError = dialog.error == DeleteSpaceError.WRONG_PASSWORD,
                keyboardType = KeyboardType.Password,
                hidden = true,
                imeAction = ImeAction.Done,
                onImeAction = { if (dialog.canDelete) onConfirm() }
            )
            FormErrorLine(
                when (dialog.error) {
                    DeleteSpaceError.WRONG_PASSWORD -> stringResource(Res.string.msg_delete_space_wrong_password)
                    DeleteSpaceError.FAILED -> stringResource(Res.string.msg_delete_space_failed)
                    null -> null
                }
            )
        },
        buttons = {
            DialogButtons(
                confirmLabel = stringResource(if (dialog.isDeleting) Res.string.btn_delete_account_deleting else Res.string.btn_delete_space),
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                destructive = true,
                confirmEnabled = dialog.canDelete
            )
        }
    )
}

/** The space's logo (the Squadify mark, as a space has none), its name and sport. */
@Composable
private fun SpaceHeader(state: MySpaceUiState.Loaded) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SpaceCrest(state.space, size = 52.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(state.space.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            Text(
                stringResource(if (state.space.sport == SportType.FUTSAL) SharedRes.string.sport_futsal else SharedRes.string.sport_football),
                style = MaterialTheme.typography.bodySmall,
                color = CarbonGray
            )
        }
    }
}

/**
 * The space's plan as a badge, this season's age groups against its limit (in amber once it's reached)
 * and how many people an age group can have, then See plans.
 */
@Composable
private fun PlanCard(plan: SpacePlan, ageGroupsThisSeason: Int, onSeePlans: () -> Unit) {
    val plans = LocalPlans.current
    SectionCard(contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.plan_card_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon,
                    modifier = Modifier.weight(1f)
                )
                // The plan's place among the server's plans: the cheapest grey, the top one solid green.
                val (badge, onBadge) = when (plans.indexOfFirst { it.plan == plan }) {
                    -1, 0 -> CarbonGray.copy(alpha = 0.08f) to CarbonGray
                    plans.lastIndex -> PitchGreen to Color.White
                    else -> PitchGreen.copy(alpha = 0.12f) to DeepGreen
                }
                Surface(shape = RoundedCornerShape(13.dp), color = badge) {
                    Text(
                        plan.displayName(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = onBadge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            Text(stringResource(Res.string.plan_card_this_season), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.7f))
            // The limits as the server sends them; until they load, the count alone.
            val details = plans.firstOrNull { it.plan == plan }
            val ageGroupLimit = details?.ageGroupLimit
            val full = ageGroupLimit != null && ageGroupsThisSeason >= ageGroupLimit
            Text(
                if (ageGroupLimit == null) stringResource(Res.string.plan_age_groups_no_limit, ageGroupsThisSeason)
                else stringResource(Res.string.plan_age_groups_of, ageGroupsThisSeason, ageGroupLimit),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (full) FontWeight.Medium else FontWeight.Normal),
                color = if (full) AmberText else DeepCarbon
            )
            if (details != null) {
                val people = details.peoplePerAgeGroupLimit
                Text(
                    when {
                        people == null -> stringResource(Res.string.plan_people_no_limit)
                        people <= 1 -> stringResource(Res.string.plan_people_just_you)
                        else -> stringResource(Res.string.plan_people_up_to, people)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeepCarbon
                )
            }
        }
        HorizontalDivider(color = SectionBorderColor)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onSeePlans).heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.btn_see_plans_row),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = PitchGreen,
                modifier = Modifier.weight(1f)
            )
            Text("›", style = MaterialTheme.typography.titleLarge, color = CarbonGray.copy(alpha = 0.5f))
        }
    }
}

/** A space's logo, or its short name / first letter until it has one, as on the clubs app's My clubs. */
@Composable
fun SpaceLogo(space: Club, size: Dp) {
    ClubLogo(
        logoUrl = space.logoUrl,
        initials = space.shortName ?: space.name.take(1).uppercase(),
        size = size,
        shape = RoundedCornerShape(12.dp),
        background = CarbonGray.copy(alpha = 0.1f),
        initialsColor = CarbonGray
    )
}

private val previewSpace = Club(id = "space-1", name = "Fontelas FC", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)

@Preview
@Composable
private fun MySpacePreview() {
    SquadifyTheme {
        CompositionLocalProvider(LocalPlans provides previewPlans) {
            MySpaceContent(MySpaceUiState.Loaded(previewSpace, SpacePlan("FREE"), ageGroupsThisSeason = 1), onRetry = {}, onSeePlans = {}, onNavigateBack = {})
        }
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun MySpaceLandscapePreview() {
    SquadifyTheme {
        CompositionLocalProvider(LocalPlans provides previewPlans) {
            MySpaceContent(MySpaceUiState.Loaded(previewSpace, SpacePlan("FREE")), onRetry = {}, onSeePlans = {}, onNavigateBack = {})
        }
    }
}

@Preview
@Composable
private fun MySpaceBasicPreview() {
    SquadifyTheme {
        CompositionLocalProvider(LocalPlans provides previewPlans) {
            MySpaceContent(MySpaceUiState.Loaded(previewSpace, SpacePlan("BASIC"), ageGroupsThisSeason = 2), onRetry = {}, onSeePlans = {}, onNavigateBack = {})
        }
    }
}

@Preview
@Composable
private fun MySpaceFailedPreview() {
    SquadifyTheme {
        CompositionLocalProvider(LocalPlans provides previewPlans) {
            MySpaceContent(MySpaceUiState.Failed, onRetry = {}, onSeePlans = {}, onNavigateBack = {})
        }
    }
}
