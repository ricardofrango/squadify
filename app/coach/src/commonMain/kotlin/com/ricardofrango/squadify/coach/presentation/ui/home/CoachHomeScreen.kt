package com.ricardofrango.squadify.coach.presentation.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.SpaceAgeGroups
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.cd_coach_menu
import squadify.app.coach.generated.resources.coach_app_name
import squadify.app.coach.generated.resources.coach_home_add_age_group
import squadify.app.coach.generated.resources.coach_home_born
import squadify.app.coach.generated.resources.coach_home_create_age_group
import squadify.app.coach.generated.resources.coach_home_empty_body
import squadify.app.coach.generated.resources.coach_home_empty_title
import squadify.app.coach.generated.resources.coach_home_join
import squadify.app.coach.generated.resources.coach_home_title
import squadify.app.coach.generated.resources.ic_join
import squadify.app.coach.generated.resources.ic_menu
import squadify.app.coach.generated.resources.msg_coach_home_failed
import squadify.app.shared.generated.resources.btn_logout
import squadify.app.shared.generated.resources.btn_retry
import squadify.app.shared.generated.resources.ic_add
import squadify.app.shared.generated.resources.ic_logout
import kotlin.time.Instant
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * The coach app's home (REQUIREMENTS §5c): one list of every age group the coach has, their own space's
 * and the ones they joined with a code, each naming its space and opening Age Group Details. Creating an
 * age group and joining one with a code are always there: in the ☰ side menu, and as buttons when the
 * list is empty. The root of the back stack.
 */
@Composable
fun CoachHomeScreen(
    onSignedOut: () -> Unit,
    onOpenAgeGroup: (ageGroupId: String) -> Unit,
    // Into the coach's own space, in its sport.
    onCreateAgeGroups: (spaceId: String, spaceName: String, sport: SportType) -> Unit,
    // No space of their own yet: it's set up with the first age group.
    onSetUpSpace: () -> Unit,
    onJoinWithCode: () -> Unit,
    // Set when a screen opened from here changed something (an age group created, a code used).
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
                CoachHomeNavigationEvent.NavigateToLogin -> onSignedOut()
            }
        }
    }

    CoachHomeContent(
        uiState = uiState,
        onRetry = viewModel::load,
        onSignOut = viewModel::onSignOutClicked,
        onOpenAgeGroup = onOpenAgeGroup,
        onCreateAgeGroup = {
            when (val space = (uiState as? CoachHomeUiState.Loaded)?.ownSpace) {
                null -> onSetUpSpace()
                else -> onCreateAgeGroups(space.id, space.name, space.sport)
            }
        },
        onJoinWithCode = onJoinWithCode
    )
}

@Composable
private fun CoachHomeContent(
    uiState: CoachHomeUiState,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onOpenAgeGroup: (String) -> Unit = {},
    onCreateAgeGroup: () -> Unit = {},
    onJoinWithCode: () -> Unit = {},
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed)
) {
    val scope = rememberCoroutineScope()
    // Closes the menu, then does what was picked.
    fun fromMenu(action: () -> Unit): () -> Unit = { scope.launch { drawerState.close() }; action() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White, modifier = Modifier.widthIn(max = 300.dp)) {
                Text(
                    stringResource(Res.string.coach_app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = DeepCarbon,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)
                )
                MenuItem(SharedRes.drawable.ic_add, stringResource(Res.string.coach_home_create_age_group), fromMenu(onCreateAgeGroup))
                MenuItem(Res.drawable.ic_join, stringResource(Res.string.coach_home_join), fromMenu(onJoinWithCode))
                MenuItem(SharedRes.drawable.ic_logout, stringResource(SharedRes.string.btn_logout), fromMenu(onSignOut))
            }
        }
    ) {
        Scaffold(
            containerColor = ScreenBackground,
            topBar = {
                SquadifyTopBar(
                    title = stringResource(Res.string.coach_home_title),
                    showBackButton = false,
                    leadingIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(painterResource(Res.drawable.ic_menu), contentDescription = stringResource(Res.string.cd_coach_menu), tint = Color.White)
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (uiState) {
                CoachHomeUiState.Loading -> Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PitchGreen)
                }

                CoachHomeUiState.Failed -> CenteredMessage(Modifier.padding(paddingValues)) {
                    Text(stringResource(Res.string.msg_coach_home_failed), style = MaterialTheme.typography.bodyMedium, color = DeepCarbon.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) {
                        Text(stringResource(SharedRes.string.btn_retry), color = PitchGreen, fontWeight = FontWeight.Bold)
                    }
                }

                is CoachHomeUiState.Loaded -> if (uiState.ageGroups.isEmpty()) {
                    CenteredMessage(Modifier.padding(paddingValues)) {
                        Text(stringResource(Res.string.coach_home_empty_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, textAlign = TextAlign.Center)
                        Text(stringResource(Res.string.coach_home_empty_body), style = MaterialTheme.typography.bodyMedium, color = CarbonGray, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onCreateAgeGroup,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PitchGreen)
                        ) {
                            Text(stringResource(Res.string.coach_home_create_age_group))
                        }
                        JoinButton(onJoinWithCode)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues).readableWidth(),
                        contentPadding = ScreenPadding.Values,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.ageGroups, key = { it.ageGroup.id }) { row -> AgeGroupRow(row) { onOpenAgeGroup(row.ageGroup.id) } }
                        item {
                            OutlinedButton(
                                onClick = onCreateAgeGroup,
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
private fun MenuItem(icon: DrawableResource, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(painterResource(icon), contentDescription = null, tint = DeepCarbon) },
        label = { Text(label, color = DeepCarbon) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
private fun JoinButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PitchGreen.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PitchGreen)
    ) {
        Text(stringResource(Res.string.coach_home_join))
    }
}

@Composable
private fun AgeGroupRow(row: HomeAgeGroup, onClick: () -> Unit) {
    SectionCard(spacing = 2.dp, onClick = onClick) {
        Text(row.ageGroup.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon)
        Text(
            stringResource(Res.string.coach_home_born, bornYears(row.ageGroup), row.spaceName),
            style = MaterialTheme.typography.bodySmall,
            color = CarbonGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "2015", or "2015–2016" for a two-year age group. */
private fun bornYears(ageGroup: AgeGroup): String =
    if (ageGroup.startYear == ageGroup.endYear) ageGroup.endYear.toString()
    else "${minOf(ageGroup.startYear, ageGroup.endYear)}–${maxOf(ageGroup.startYear, ageGroup.endYear)}"

@Composable
private fun CenteredMessage(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().screenContentPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

private fun previewClub(id: String, name: String) =
    Club(id = id, name = name, status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)

private val previewSpaces = listOf(
    SpaceAgeGroups(
        previewClub("space-1", "Fontelas FC"), isOwner = true,
        ageGroups = listOf(
            AgeGroup(id = "ag-1", clubId = "space-1", name = "Sub 11", seasonId = "s1", startYear = 2016, endYear = 2016, teamSize = 7),
            AgeGroup(id = "ag-2", clubId = "space-1", name = "Sub 13", seasonId = "s1", startYear = 2015, endYear = 2014, teamSize = 9)
        )
    ),
    SpaceAgeGroups(
        previewClub("space-2", "Ana's team"), isOwner = false,
        ageGroups = listOf(AgeGroup(id = "ag-3", clubId = "space-2", name = "Sub 9", seasonId = "s2", startYear = 2018, endYear = 2018, teamSize = 5))
    )
)

@Preview
@Composable
private fun CoachHomePreview() {
    SquadifyTheme {
        CoachHomeContent(CoachHomeUiState.Loaded.of(previewSpaces), onRetry = {}, onSignOut = {})
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun CoachHomeLandscapePreview() {
    SquadifyTheme {
        CoachHomeContent(CoachHomeUiState.Loaded.of(previewSpaces), onRetry = {}, onSignOut = {})
    }
}

@Preview
@Composable
private fun CoachHomeEmptyPreview() {
    SquadifyTheme {
        CoachHomeContent(CoachHomeUiState.Loaded.of(emptyList()), onRetry = {}, onSignOut = {})
    }
}

@Preview(widthDp = 900, heightDp = 480)
@Composable
private fun CoachHomeEmptyLandscapePreview() {
    SquadifyTheme {
        CoachHomeContent(CoachHomeUiState.Loaded.of(emptyList()), onRetry = {}, onSignOut = {})
    }
}

@Preview
@Composable
private fun CoachHomeMenuPreview() {
    SquadifyTheme {
        CoachHomeContent(CoachHomeUiState.Loaded.of(previewSpaces), onRetry = {}, onSignOut = {}, drawerState = rememberDrawerState(DrawerValue.Open))
    }
}

@Preview
@Composable
private fun CoachHomeFailedPreview() {
    SquadifyTheme {
        CoachHomeContent(CoachHomeUiState.Failed, onRetry = {}, onSignOut = {})
    }
}
