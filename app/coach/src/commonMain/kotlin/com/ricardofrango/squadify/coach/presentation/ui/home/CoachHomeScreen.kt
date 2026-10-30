package com.ricardofrango.squadify.coach.presentation.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.CoachSpace
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import com.ricardofrango.squadify.presentation.ui.theme.screenContentPadding
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.coach_home_add_age_group
import squadify.app.coach.generated.resources.coach_home_age_groups
import squadify.app.coach.generated.resources.coach_home_born
import squadify.app.coach.generated.resources.coach_home_no_age_groups
import squadify.app.coach.generated.resources.msg_coach_home_failed
import squadify.app.shared.generated.resources.btn_logout
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.sport_football
import squadify.app.shared.generated.resources.sport_futsal
import kotlin.time.Instant
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * The coach app's home: the space (its name and sport, never called a club) and the age groups the
 * coach sees, all of them for the owner and their own for staff, each opening Age Group Details. The
 * owner can add age groups. The root of the back stack.
 */
@Composable
fun CoachHomeScreen(
    onNavigateToSetup: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenAgeGroup: (ageGroupId: String) -> Unit,
    // The owner only: Create age groups, in the space's sport.
    onCreateAgeGroups: (spaceId: String, spaceName: String, sport: SportType) -> Unit,
    // Set when a screen opened from here changed something (an age group created or edited).
    refresh: Boolean = false,
    onRefreshed: () -> Unit = {},
    viewModel: CoachHomeViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(refresh) {
        if (refresh) {
            onRefreshed()
            viewModel.load()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                CoachHomeNavigationEvent.NavigateToSetup -> onNavigateToSetup()
                CoachHomeNavigationEvent.NavigateToLogin -> onSignedOut()
            }
        }
    }

    CoachHomeContent(
        uiState = uiState,
        onRetry = viewModel::load,
        onSignOut = viewModel::onSignOutClicked,
        onOpenAgeGroup = onOpenAgeGroup,
        onCreateAgeGroups = { space -> onCreateAgeGroups(space.id, space.name, space.sport) }
    )
}

@Composable
private fun CoachHomeContent(
    uiState: CoachHomeUiState,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onOpenAgeGroup: (String) -> Unit = {},
    onCreateAgeGroups: (Club) -> Unit = {}
) {
    val space = (uiState as? CoachHomeUiState.Loaded)?.space?.space
    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            SquadifyTopBar(
                title = space?.name.orEmpty(),
                subtitle = space?.let { stringResource(if (it.sport == SportType.FUTSAL) SharedRes.string.sport_futsal else SharedRes.string.sport_football) },
                showBackButton = false,
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text(stringResource(SharedRes.string.btn_logout), color = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        when (uiState) {
            CoachHomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PitchGreen)
            }

            CoachHomeUiState.Failed -> CenteredMessage(stringResource(Res.string.msg_coach_home_failed), Modifier.padding(paddingValues)) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(SharedRes.string.btn_retry), color = PitchGreen, fontWeight = FontWeight.Bold)
                }
            }

            is CoachHomeUiState.Loaded -> if (uiState.ageGroups.isEmpty() && !uiState.space.isOwner) {
                CenteredMessage(stringResource(Res.string.coach_home_no_age_groups), Modifier.padding(paddingValues))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).readableWidth(),
                    contentPadding = ScreenPadding.Values,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(Res.string.coach_home_age_groups).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = DeepCarbon.copy(alpha = 0.5f)
                        )
                    }
                    items(uiState.ageGroups, key = { it.id }) { ageGroup -> AgeGroupRow(ageGroup) { onOpenAgeGroup(ageGroup.id) } }
                    // Only the owner creates age groups (REQUIREMENTS §5c).
                    if (uiState.space.isOwner) {
                        item {
                            OutlinedButton(
                                onClick = { onCreateAgeGroups(uiState.space.space) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, PitchGreen.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PitchGreen)
                            ) {
                                Text(stringResource(Res.string.coach_home_add_age_group))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgeGroupRow(ageGroup: AgeGroup, onClick: () -> Unit) {
    SectionCard(spacing = 2.dp, onClick = onClick) {
        Text(ageGroup.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        Text(stringResource(Res.string.coach_home_born, bornYears(ageGroup)), style = MaterialTheme.typography.bodySmall, color = CarbonGray)
    }
}

/** "2015", or "2015–2016" for a two-year age group. */
private fun bornYears(ageGroup: AgeGroup): String =
    if (ageGroup.startYear == ageGroup.endYear) ageGroup.endYear.toString()
    else "${minOf(ageGroup.startYear, ageGroup.endYear)}–${maxOf(ageGroup.startYear, ageGroup.endYear)}"

@Composable
private fun CenteredMessage(text: String, modifier: Modifier, action: @Composable () -> Unit = {}) {
    Box(modifier.fillMaxSize().screenContentPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            action()
        }
    }
}

private val previewSpace = CoachSpace(
    space = Club(id = "space-1", name = "Fontelas FC", status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL),
    isOwner = true
)

private val previewAgeGroups = listOf(
    AgeGroup(id = "ag-1", clubId = "space-1", name = "Sub 11", seasonId = "s1", startYear = 2016, endYear = 2016, teamSize = 7),
    AgeGroup(id = "ag-2", clubId = "space-1", name = "Sub 13", seasonId = "s1", startYear = 2015, endYear = 2014, teamSize = 9)
)

@Preview
@Composable
private fun CoachHomePreview() {
    SquadifyTheme {
        CoachHomeContent(CoachHomeUiState.Loaded(previewSpace, previewAgeGroups), onRetry = {}, onSignOut = {})
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun CoachHomeLandscapePreview() {
    SquadifyTheme {
        CoachHomeContent(CoachHomeUiState.Loaded(previewSpace, previewAgeGroups), onRetry = {}, onSignOut = {})
    }
}

@Preview
@Composable
private fun CoachHomeFailedPreview() {
    SquadifyTheme {
        CoachHomeContent(CoachHomeUiState.Failed, onRetry = {}, onSignOut = {})
    }
}
