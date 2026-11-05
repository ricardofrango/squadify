package com.ricardofrango.squadify.coach.presentation.ui.space

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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.theme.AlertRed
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.msg_my_space_failed
import squadify.app.coach.generated.resources.my_space_logo_intro
import squadify.app.coach.generated.resources.my_space_title
import squadify.app.shared.generated.resources.btn_remove_logo
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.label_add_logo
import squadify.app.shared.generated.resources.label_change_logo
import squadify.app.shared.generated.resources.msg_logo_hint
import squadify.app.shared.generated.resources.msg_logo_remove_failed
import squadify.app.shared.generated.resources.msg_logo_saving
import squadify.app.shared.generated.resources.msg_logo_too_big
import squadify.app.shared.generated.resources.msg_logo_upload_failed
import squadify.app.shared.generated.resources.sport_football
import squadify.app.shared.generated.resources.sport_futsal
import kotlin.time.Instant
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * The owner's own space, from the ☰ menu: its name and sport, and its logo with "Add logo" / "Change
 * logo" and "Remove", saved straight away. [onLogoChanged] runs each time it changes, so Home reloads.
 * Under it, the Plan card: the plan, this season's age groups against its limit, and See plans.
 */
@Composable
fun MySpaceScreen(
    onNavigateBack: () -> Unit,
    onLogoChanged: () -> Unit,
    onSeePlans: () -> Unit,
    viewModel: MySpaceViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.logoChanged.collect { onLogoChanged() } }
    MySpaceContent(
        uiState = uiState,
        onLogoPicked = viewModel::onLogoPicked,
        onRemoveLogo = viewModel::onRemoveLogo,
        onRetry = viewModel::load,
        onSeePlans = onSeePlans,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun MySpaceContent(
    uiState: MySpaceUiState,
    onLogoPicked: (ByteArray) -> Unit,
    onRemoveLogo: () -> Unit,
    onRetry: () -> Unit,
    onSeePlans: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
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
                SectionCard { SpaceLogoHeader(uiState, onLogoPicked, onRemoveLogo) }
                Text(stringResource(Res.string.my_space_logo_intro), style = MaterialTheme.typography.bodySmall, color = CarbonGray)
                PlanCard(uiState.plan, uiState.ageGroupsThisSeason, onSeePlans = onSeePlans)
            }
        }
    }
}

/** The logo (or initials) with the space's name and sport, and the logo's actions under them. */
@Composable
private fun SpaceLogoHeader(state: MySpaceUiState.Loaded, onLogoPicked: (ByteArray) -> Unit, onRemoveLogo: () -> Unit) {
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        if (file != null) scope.launch { onLogoPicked(file.readBytes()) }
    }
    val enabled = !state.isUpdatingLogo
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            SpaceLogo(state.space, size = 64.dp)
            if (state.isUpdatingLogo) CircularProgressIndicator(Modifier.size(64.dp), color = PitchGreen, strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(state.space.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
            Text(
                stringResource(if (state.space.sport == SportType.FUTSAL) SharedRes.string.sport_futsal else SharedRes.string.sport_football),
                style = MaterialTheme.typography.bodySmall,
                color = CarbonGray
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (state.space.logoUrl == null) SharedRes.string.label_add_logo else SharedRes.string.label_change_logo),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (enabled) PitchGreen else PitchGreen.copy(alpha = 0.4f),
                    modifier = Modifier.clickable(enabled = enabled) { picker.launch() }.padding(vertical = 4.dp)
                )
                if (state.space.logoUrl != null) {
                    Text(" · ", color = DeepCarbon.copy(alpha = 0.3f))
                    Text(
                        stringResource(SharedRes.string.btn_remove_logo),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (enabled) AlertRed else AlertRed.copy(alpha = 0.4f),
                        modifier = Modifier.clickable(enabled = enabled, onClick = onRemoveLogo).padding(vertical = 4.dp)
                    )
                }
            }
            Text(
                state.logoProblem?.let {
                    stringResource(
                        when (it) {
                            LogoProblem.TOO_BIG -> SharedRes.string.msg_logo_too_big
                            LogoProblem.UPLOAD_FAILED -> SharedRes.string.msg_logo_upload_failed
                            LogoProblem.REMOVE_FAILED -> SharedRes.string.msg_logo_remove_failed
                        }
                    )
                } ?: stringResource(if (state.isUpdatingLogo) SharedRes.string.msg_logo_saving else SharedRes.string.msg_logo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.logoProblem != null) MaterialTheme.colorScheme.error else DeepCarbon.copy(alpha = 0.5f)
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
            MySpaceContent(MySpaceUiState.Loaded(previewSpace, SpacePlan("FREE"), ageGroupsThisSeason = 1), onLogoPicked = {}, onRemoveLogo = {}, onRetry = {}, onSeePlans = {}, onNavigateBack = {})
        }
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun MySpaceLandscapePreview() {
    SquadifyTheme {
        CompositionLocalProvider(LocalPlans provides previewPlans) {
            MySpaceContent(MySpaceUiState.Loaded(previewSpace, SpacePlan("FREE")), onLogoPicked = {}, onRemoveLogo = {}, onRetry = {}, onSeePlans = {}, onNavigateBack = {})
        }
    }
}

@Preview
@Composable
private fun MySpaceBasicPreview() {
    SquadifyTheme {
        CompositionLocalProvider(LocalPlans provides previewPlans) {
            MySpaceContent(MySpaceUiState.Loaded(previewSpace, SpacePlan("BASIC"), ageGroupsThisSeason = 2), onLogoPicked = {}, onRemoveLogo = {}, onRetry = {}, onSeePlans = {}, onNavigateBack = {})
        }
    }
}

@Preview
@Composable
private fun MySpaceProblemPreview() {
    SquadifyTheme {
        CompositionLocalProvider(LocalPlans provides previewPlans) {
            MySpaceContent(MySpaceUiState.Loaded(previewSpace, SpacePlan("FREE"), logoProblem = LogoProblem.TOO_BIG), onLogoPicked = {}, onRemoveLogo = {}, onRetry = {}, onSeePlans = {}, onNavigateBack = {})
        }
    }
}

@Preview
@Composable
private fun MySpaceFailedPreview() {
    SquadifyTheme {
        CompositionLocalProvider(LocalPlans provides previewPlans) {
            MySpaceContent(MySpaceUiState.Failed, onLogoPicked = {}, onRemoveLogo = {}, onRetry = {}, onSeePlans = {}, onNavigateBack = {})
        }
    }
}
