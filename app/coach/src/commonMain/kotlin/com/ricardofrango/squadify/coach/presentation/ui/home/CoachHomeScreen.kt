package com.ricardofrango.squadify.coach.presentation.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.ricardofrango.squadify.coach.domain.CoachAds
import com.ricardofrango.squadify.coach.presentation.ads.rememberPrivacyOptions
import com.ricardofrango.squadify.coach.presentation.ui.space.AgeGroupCrest
import com.ricardofrango.squadify.coach.presentation.ui.space.SpaceLogo
import com.ricardofrango.squadify.coach.presentation.ui.space.clubColor
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubStatus
import com.ricardofrango.squadify.model.SpaceAgeGroups
import com.ricardofrango.squadify.model.SpacePlan
import com.ricardofrango.squadify.model.SportType
import com.ricardofrango.squadify.presentation.ui.components.AdBanner
import com.ricardofrango.squadify.presentation.ui.components.ErrorScreen
import com.ricardofrango.squadify.presentation.ui.components.ScreenWindowInsets
import com.ricardofrango.squadify.presentation.ui.components.SectionCard
import com.ricardofrango.squadify.presentation.ui.components.SquadifyMark
import com.ricardofrango.squadify.presentation.ui.components.SquadifyTopBar
import com.ricardofrango.squadify.presentation.ui.components.displayName
import com.ricardofrango.squadify.presentation.ui.components.initialsOf
import com.ricardofrango.squadify.presentation.ui.components.readableWidth
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import com.ricardofrango.squadify.presentation.ui.theme.ScreenBackground
import com.ricardofrango.squadify.presentation.ui.theme.ScreenPadding
import com.ricardofrango.squadify.presentation.ui.theme.SquadifyTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import squadify.app.coach.generated.resources.Res
import squadify.app.coach.generated.resources.cd_coach_menu
import squadify.app.coach.generated.resources.coach_app_name
import squadify.app.coach.generated.resources.coach_home_age_groups
import squadify.app.coach.generated.resources.coach_home_create_age_groups
import squadify.app.coach.generated.resources.coach_home_default_space_name
import squadify.app.coach.generated.resources.coach_home_first_age_groups
import squadify.app.coach.generated.resources.coach_home_got_code
import squadify.app.coach.generated.resources.coach_home_join
import squadify.app.coach.generated.resources.coach_home_my_space
import squadify.app.coach.generated.resources.coach_home_no_age_groups
import squadify.app.coach.generated.resources.coach_home_opponents
import squadify.app.coach.generated.resources.coach_home_shared_spaces
import squadify.app.coach.generated.resources.coach_home_title
import squadify.app.coach.generated.resources.coach_menu_account
import squadify.app.coach.generated.resources.coach_menu_privacy_options
import squadify.app.coach.generated.resources.ic_join
import squadify.app.coach.generated.resources.ic_menu
import squadify.app.coach.generated.resources.ic_plans
import squadify.app.coach.generated.resources.msg_coach_home_failed
import squadify.app.coach.generated.resources.plans_title
import squadify.app.coach.generated.resources.space_details_owned_by
import squadify.app.shared.generated.resources.btn_logout
import squadify.app.shared.generated.resources.ic_account
import squadify.app.shared.generated.resources.ic_add
import squadify.app.shared.generated.resources.ic_lock
import squadify.app.shared.generated.resources.ic_logout
import kotlin.time.Instant
import squadify.app.shared.generated.resources.Res as SharedRes

/**
 * The coach app's home (design "Os meus espaços"): the coach's own space, named after them, with its age
 * groups' crests and how many age groups and opponents it has, then the spaces they're staff in (whose,
 * and their age groups there). Tapping a space opens its details. Before their first age groups, their own
 * space's card offers to create them; a code from another coach joins theirs. The ☰ side menu has
 * creating age groups, joining with a code, Plans (an owner), Account and Sign out. The root of the back
 * stack.
 */
@Composable
fun CoachHomeScreen(
    onSignedOut: () -> Unit,
    onOpenSpace: (spaceId: String) -> Unit,
    // Into the coach's own space, in its sport.
    onCreateAgeGroups: (space: Club) -> Unit,
    // No space of their own yet: it's set up with the first age group.
    onSetUpSpace: () -> Unit,
    onJoinWithCode: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenPlans: () -> Unit = {},
    // Set when a screen opened from here changed something (age groups created, a code used).
    refresh: Boolean = false,
    onRefreshed: () -> Unit = {},
    viewModel: CoachHomeViewModel = koinViewModel()
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // The Free plan's banner stands for the coach's own space here (CoachAds.homeSpaceId).
    val adSpaceId by koinInject<CoachAds>().homeSpaceId.collectAsStateWithLifecycle()

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
        onOpenSpace = onOpenSpace,
        onCreateAgeGroups = {
            when (val own = (uiState as? CoachHomeUiState.Loaded)?.ownSpace) {
                null -> onSetUpSpace()
                else -> onCreateAgeGroups(own.space)
            }
        },
        onJoinWithCode = onJoinWithCode,
        onOpenAccount = onOpenAccount,
        onOpenPlans = onOpenPlans,
        adSpaceId = adSpaceId,
        onPrivacyOptions = rememberPrivacyOptions()
    )
}

@Composable
private fun CoachHomeContent(
    uiState: CoachHomeUiState,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSpace: (String) -> Unit = {},
    onCreateAgeGroups: () -> Unit = {},
    onJoinWithCode: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenPlans: () -> Unit = {},
    adSpaceId: String? = null,
    // The ads' consent form again, where the user must be able to change it (the EU and UK); null otherwise.
    onPrivacyOptions: (() -> Unit)? = null,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed)
) {
    val scope = rememberCoroutineScope()
    // Closes the menu, then does what was picked.
    fun fromMenu(action: () -> Unit): () -> Unit = { scope.launch { drawerState.close() }; action() }
    // Back closes the open menu first; Home is the stack's root, so without this it left the app.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = drawerState.isOpen,
        onBackCompleted = { scope.launch { drawerState.close() } }
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // Opens from the start edge: its items keep clear of the camera cutout there on a phone lying
            // down too (Material's default only covers the system bars).
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                modifier = Modifier.widthIn(max = 300.dp),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            ) {
                // Scrolls: lying down on a phone the items run past the bottom (Sign out was cut off).
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(Res.string.coach_app_name),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = DeepCarbon,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)
                    )
                    MenuItem(SharedRes.drawable.ic_add, stringResource(Res.string.coach_home_create_age_groups), fromMenu(onCreateAgeGroups))
                    MenuItem(Res.drawable.ic_join, stringResource(Res.string.coach_home_join), fromMenu(onJoinWithCode))
                    // Only once they own a space: its plan.
                    if ((uiState as? CoachHomeUiState.Loaded)?.ownSpace != null) {
                        MenuItem(Res.drawable.ic_plans, stringResource(Res.string.plans_title), fromMenu(onOpenPlans))
                    }
                    MenuItem(SharedRes.drawable.ic_account, stringResource(Res.string.coach_menu_account), fromMenu(onOpenAccount))
                    onPrivacyOptions?.let { MenuItem(SharedRes.drawable.ic_lock, stringResource(Res.string.coach_menu_privacy_options), fromMenu(it)) }
                    MenuItem(SharedRes.drawable.ic_logout, stringResource(SharedRes.string.btn_logout), fromMenu(onSignOut))
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = ScreenWindowInsets,
            containerColor = ScreenBackground,
            // The Free plan's banner, at the bottom.
            bottomBar = { AdBanner(spaceId = adSpaceId, alone = true) },
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

                CoachHomeUiState.Failed -> ErrorScreen(title = stringResource(Res.string.msg_coach_home_failed), body = null, onRetry = onRetry, modifier = Modifier.fillMaxSize().padding(paddingValues))

                is CoachHomeUiState.Loaded -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).readableWidth(),
                    contentPadding = ScreenPadding.Values,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "own-header") { ListHeader(stringResource(Res.string.coach_home_my_space), first = true) }
                    item(key = "own") {
                        OwnSpaceCard(
                            space = uiState.ownSpace,
                            defaultName = stringResource(Res.string.coach_home_default_space_name, uiState.userName.orEmpty()).takeIf { uiState.userName != null },
                            onOpen = uiState.ownSpace?.let { own -> { onOpenSpace(own.space.id) } },
                            onCreateAgeGroups = onCreateAgeGroups
                        )
                    }
                    if (uiState.otherSpaces.isNotEmpty()) {
                        item(key = "shared-header") { ListHeader(stringResource(Res.string.coach_home_shared_spaces)) }
                        items(uiState.otherSpaces, key = { it.space.id }) { space ->
                            SharedSpaceCard(space, onClick = { onOpenSpace(space.space.id) })
                        }
                    }
                    item(key = "join") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            // Before their own age groups, a code is the other way in.
                            if (uiState.ownSpace == null || uiState.ownSpace.ageGroups.isEmpty()) {
                                Text(stringResource(Res.string.coach_home_got_code), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                            }
                            JoinButton(onJoinWithCode)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The coach's own space: a green pitch with the Squadify mark (or its image) and its plan, its name, how
 * many age groups and opponents, and its age groups' crests with the clubs they play for. Before their
 * first age groups (no space yet, or none in it), [defaultName] and the button to create them.
 */
@Composable
private fun OwnSpaceCard(space: SpaceAgeGroups?, defaultName: String?, onOpen: (() -> Unit)?, onCreateAgeGroups: () -> Unit) {
    val empty = space == null || space.ageGroups.isEmpty()
    SectionCard(onClick = onOpen.takeUnless { empty }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        PitchBanner(space)
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        space?.space?.name ?: defaultName.orEmpty(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = DeepCarbon,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (empty) stringResource(Res.string.coach_home_no_age_groups)
                        else "${pluralStringResource(Res.plurals.coach_home_age_groups, space.ageGroups.size, space.ageGroups.size)} · " +
                            pluralStringResource(Res.plurals.coach_home_opponents, space.opponents, space.opponents),
                        style = MaterialTheme.typography.bodySmall,
                        color = CarbonGray.copy(alpha = 0.7f)
                    )
                }
                if (!empty) Text("›", style = MaterialTheme.typography.titleLarge, color = CarbonGray.copy(alpha = 0.4f))
            }
            if (empty) {
                Text(stringResource(Res.string.coach_home_first_age_groups), style = MaterialTheme.typography.bodySmall, color = DeepCarbon.copy(alpha = 0.8f))
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onCreateAgeGroups,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PitchGreen)
                ) {
                    Icon(painterResource(SharedRes.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.coach_home_create_age_groups), fontWeight = FontWeight.Bold)
                }
            } else {
                CrestStrip(space)
            }
        }
    }
}

/** The card's top: the space's image, or a green pitch with the Squadify mark; its plan in the corner. */
@Composable
private fun PitchBanner(space: SpaceAgeGroups?) {
    Box(Modifier.fillMaxWidth().height(84.dp).background(PitchGreen), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val line = Color.White.copy(alpha = 0.22f)
            val stroke = Stroke(width = 1.5.dp.toPx())
            val inset = 8.dp.toPx()
            drawRect(line, topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2), style = stroke)
            drawLine(line, Offset(size.width / 2, inset), Offset(size.width / 2, size.height - inset), strokeWidth = stroke.width)
            drawCircle(line, radius = size.height * 0.22f, center = center, style = stroke)
            val boxHeight = size.height * 0.5f
            val boxWidth = size.height * 0.3f
            drawRect(line, topLeft = Offset(inset, (size.height - boxHeight) / 2), size = Size(boxWidth, boxHeight), style = stroke)
            drawRect(line, topLeft = Offset(size.width - inset - boxWidth, (size.height - boxHeight) / 2), size = Size(boxWidth, boxHeight), style = stroke)
        }
        if (space?.space?.logoUrl != null) {
            SpaceLogo(space.space, size = 56.dp)
        } else {
            SquadifyMark(size = 60.dp, lightDotColor = Color.White, darkDotColor = Color.White.copy(alpha = 0.85f), onTile = false)
        }
        space?.plan?.let { plan ->
            Text(
                plan.displayName(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = DeepCarbon,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
    }
}

/** Up to 6 of the space's age groups' crests, then the clubs they play for. */
@Composable
private fun CrestStrip(space: SpaceAgeGroups) {
    val clubs = clubsOf(space)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        space.ageGroups.sortedWith(compareBy<AgeGroup> { it.name.lowercase() }).take(6).forEach { AgeGroupCrest(it, size = 22.dp) }
        if (clubs.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(clubs.joinToString(" · ") { it.name }, style = MaterialTheme.typography.labelSmall, color = CarbonGray.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A space they're staff in: its image (or its owner's initials), its name, whose it is, and their age groups there. */
@Composable
private fun SharedSpaceCard(space: SpaceAgeGroups, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (space.space.logoUrl != null) {
                SpaceLogo(space.space, size = 44.dp)
            } else {
                val owner = space.ownerName ?: space.space.name
                val names = owner.split(" ").filter { it.isNotBlank() }
                OwnerInitials(initialsOf(names.firstOrNull().orEmpty(), names.drop(1).lastOrNull().orEmpty()), clubColor(owner))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(space.space.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
                space.ownerName?.let {
                    Text(stringResource(Res.string.space_details_owned_by, it), style = MaterialTheme.typography.bodySmall, color = CarbonGray.copy(alpha = 0.7f))
                }
                space.ageGroups.forEach { ageGroup ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        AgeGroupCrest(ageGroup, size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(ageGroup.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = PitchGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = CarbonGray.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun OwnerInitials(initials: String, color: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(initials, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

// A section's title over its rows, as the shared screens' section headers; the gap above sets it apart
// from the section before.
@Composable
private fun ListHeader(text: String, first: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = CarbonGray.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = if (first) 0.dp else 12.dp)
    )
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
        Icon(painterResource(Res.drawable.ic_join), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(Res.string.coach_home_join))
    }
}

private fun previewClub(id: String, name: String) =
    Club(id = id, name = name, status = ClubStatus.ACTIVE, createdAt = Instant.fromEpochMilliseconds(0), sport = SportType.FOOTBALL)

private fun previewAgeGroup(id: String, spaceId: String, name: String, year: Int) =
    AgeGroup(id = id, clubId = spaceId, name = name, seasonId = "s1", startYear = year, endYear = year, teamSize = 7)

private val previewSpaces = listOf(
    SpaceAgeGroups(
        previewClub("space-1", "Espaço de Ana Silva"), isOwner = true, plan = SpacePlan("PRO"), opponents = 12,
        ageGroups = listOf(
            previewAgeGroup("ag-1", "space-1", "Povoense - Sub 12", 2015),
            previewAgeGroup("ag-2", "space-1", "Povoense - Sub 13", 2014),
            previewAgeGroup("ag-3", "space-1", "CF Santa Iria - Sub 9", 2018)
        )
    ),
    SpaceAgeGroups(
        previewClub("space-2", "Espaço de João Pereira"), isOwner = false, plan = SpacePlan("FREE"), ownerName = "João Pereira",
        ageGroups = listOf(previewAgeGroup("ag-4", "space-2", "Ericeira - Sub 11", 2016), previewAgeGroup("ag-5", "space-2", "Ericeira - Sub 12", 2015))
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
        CoachHomeContent(CoachHomeUiState.Loaded.of(emptyList(), userName = "Ana Silva"), onRetry = {}, onSignOut = {})
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
