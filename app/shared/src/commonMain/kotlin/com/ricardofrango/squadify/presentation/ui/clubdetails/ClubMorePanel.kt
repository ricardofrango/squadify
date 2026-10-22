package com.ricardofrango.squadify.presentation.ui.clubdetails

import com.ricardofrango.squadify.presentation.ui.theme.OnChromeAccent
import com.ricardofrango.squadify.presentation.ui.theme.ChromeAccent
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.ViewerAgeGroup
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.*
import kotlin.math.roundToInt

// The club's "More" panel (design 37): a full-screen panel that slides in from the right, replacing
// the old dropdown and the header's account icon. A dark account block on top, then grouped rows --
// CLUB · ADD / CREATE · ACCOUNT -- shown by permission, Log out last, the version at the bottom.

private val LogoutRed = Color(0xFFC62828)
private val RowShape = RoundedCornerShape(10.dp)

/** One action in the panel. [scope] is the second line ("Sub-15, Sub-17" for a coach's actions). */
internal data class MoreRow(
    val key: String,
    val icon: DrawableResource,
    val label: StringResource,
    val sub: String? = null,
    val destructive: Boolean = false,
    val opensScreen: Boolean = true,
    val onClick: () -> Unit
)

internal data class MoreGroup(val label: StringResource, val rows: List<MoreRow>)

/** What the panel needs about the viewer, from ClubDetails (server-computed permissions). */
internal data class MoreViewer(
    val role: ClubRole?,
    val ageGroups: List<ViewerAgeGroup>,
    val clubName: String
)

@Composable
private fun roleChip(viewer: MoreViewer): String {
    // Club role first (admin/coordinator), then what they do in an age group, else Member.
    fun names(role: TeamRole) = viewer.ageGroups.filter { it.role == role }.joinToString(", ") { it.name }
    return when {
        viewer.role == ClubRole.CLUB_ADMIN -> stringResource(Res.string.more_role_admin)
        viewer.role == ClubRole.COORDINATOR -> stringResource(Res.string.more_role_coordinator)
        viewer.ageGroups.any { it.role == TeamRole.COACH } -> "${stringResource(Res.string.more_role_coach)} · ${names(TeamRole.COACH)}"
        viewer.ageGroups.any { it.role == TeamRole.STAFF } -> "${stringResource(Res.string.more_role_staff)} · ${names(TeamRole.STAFF)}"
        else -> stringResource(Res.string.more_role_member)
    }
}

private fun initialsOf(name: String) =
    name.split(" ").filter { it.isNotBlank() }.let { parts -> listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull()) }
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "?" }

/**
 * The panel, drawn over the whole club screen. In: slides from the right over 250ms (decelerating)
 * while the screen behind dims to 35% black. Out: 200ms (accelerating). Closes with ✕, system back,
 * a swipe right (past 30% of the width, or a quick flick), Esc on web/desktop, or More again.
 */
@Composable
internal fun ClubMorePanel(
    open: Boolean,
    account: MoreAccount?,
    viewer: MoreViewer,
    groups: List<MoreGroup>,
    onOpenAccount: () -> Unit,
    onLogOut: () -> Unit,
    onClose: () -> Unit
) {
    val scrim by animateFloatAsState(if (open) 0.35f else 0f, tween(if (open) 250 else 200), label = "scrim")
    var confirmLogout by remember { mutableStateOf(false) }
    // Swipe-to-close: how far the panel has been dragged (0..1 of its width), so the scrim fades with
    // the finger, and whether it was closed by a swipe -- then the panel has already slid all the way
    // out and the exit animation is skipped (replaying it made the panel jump back and slide again).
    var swipeProgress by remember { mutableFloatStateOf(0f) }
    var swipeClosed by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (open) { swipeClosed = false; swipeProgress = 0f } }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = open,
        onBackCompleted = onClose
    )

    if (scrim > 0f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim * (1f - swipeProgress)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = open, onClick = onClose)
        )
    }

    AnimatedVisibility(
        visible = open,
        enter = slideInHorizontally(tween(250, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) { it } + fadeIn(tween(120)),
        exit = if (swipeClosed) ExitTransition.None
        else slideOutHorizontally(tween(200, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(200))
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val landscape = maxWidth > maxHeight && maxWidth >= 600.dp
            // Swipe right to close: the panel follows the finger. Released past 30% of the width or with
            // a quick flick, it carries on to the right from where it is, at the release velocity, and
            // closes; otherwise it springs back. One offset drives both, so there's never a jump.
            var drag by remember { mutableFloatStateOf(0f) }
            fun moveTo(x: Float) { drag = x; swipeProgress = (x / widthPx).coerceIn(0f, 1f) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

            Box(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(drag.roundToInt(), 0) }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> moveTo((drag + delta).coerceAtLeast(0f)) },
                        onDragStopped = { velocity ->
                            val close = drag > widthPx * 0.3f || velocity > 1500f
                            animate(
                                initialValue = drag,
                                targetValue = if (close) widthPx else 0f,
                                initialVelocity = velocity,
                                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, visibilityThreshold = 1f)
                            ) { value, _ -> moveTo(value) }
                            if (close) { swipeClosed = true; onClose() }
                        }
                    )
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { e -> if (e.type == KeyEventType.KeyUp && e.key == Key.Escape) { onClose(); true } else false }
                    .background(Color.White)
            ) {
                if (landscape) {
                    LandscapeLayout(account, viewer, groups, onOpenAccount, onClose, onLogOut = { confirmLogout = true })
                } else {
                    PortraitLayout(account, viewer, groups, onOpenAccount, onClose, onLogOut = { confirmLogout = true })
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = Color.White,
            titleContentColor = DeepCarbon,
            textContentColor = DeepCarbon.copy(alpha = 0.75f),
            title = { Text(stringResource(Res.string.more_log_out_title)) },
            text = { Text(stringResource(Res.string.more_log_out_body)) },
            confirmButton = { TextButton(onClick = { confirmLogout = false; onLogOut() }) { Text(stringResource(Res.string.more_log_out), color = LogoutRed, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(Res.string.btn_cancel), color = CarbonGray) } }
        )
    }
}

@Composable
private fun PortraitLayout(
    account: MoreAccount?,
    viewer: MoreViewer,
    groups: List<MoreGroup>,
    onOpenAccount: () -> Unit,
    onClose: () -> Unit,
    onLogOut: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        AccountBlock(account, viewer, onOpenAccount, onClose)
        // With few rows the empty space falls below them, above the version line.
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            groups.forEach { group -> GroupSection(group, onClose, onLogOut) }
        }
        Version(account)
    }
}

@Composable
private fun LandscapeLayout(
    account: MoreAccount?,
    viewer: MoreViewer,
    groups: List<MoreGroup>,
    onOpenAccount: () -> Unit,
    onClose: () -> Unit,
    onLogOut: () -> Unit
) {
    // 37g: the account block becomes a 300dp dark column; the groups sit in at most two columns,
    // 560dp wide at most, so rows never stretch across the screen.
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp).fillMaxHeight().background(ClubChrome)) {
            AccountBlock(account, viewer, onOpenAccount, onClose)
            Spacer(Modifier.weight(1f))
            Version(account, onDark = true)
        }
        val create = groups.firstOrNull { it.label == Res.string.more_group_create }
        val rest = groups.filter { it != create }
        Row(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.widthIn(max = 280.dp).weight(1f, fill = false)) { rest.forEach { GroupSection(it, onClose, onLogOut) } }
            if (create != null) Column(Modifier.widthIn(max = 280.dp).weight(1f, fill = false)) { GroupSection(create, onClose, onLogOut) }
        }
    }
}

@Composable
private fun AccountBlock(account: MoreAccount?, viewer: MoreViewer, onOpenAccount: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(ClubChrome).statusBarsPadding().padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Text("✕", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
        // The whole row opens Account -- it replaces the header's old account icon.
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenAccount).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(52.dp).background(ChromeAccent.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                Text(account?.let { initialsOf(it.name) } ?: "", color = ChromeAccent, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(account?.name ?: "", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(account?.email ?: "", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)) {
            Text(
                roleChip(viewer),
                color = OnChromeAccent,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.background(ChromeAccent, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(viewer.clubName, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GroupSection(group: MoreGroup, onClose: () -> Unit, onLogOut: () -> Unit) {
    if (group.rows.isEmpty()) return
    Text(
        stringResource(group.label).uppercase(),
        color = DeepCarbon.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
    )
    group.rows.forEach { row ->
        // A row that opens a screen doesn't close the panel: the new screen slides in from the right
        // over it, and the panel is gone when the user comes back (see ClubDetailsScreen's moreOpen).
        MoreRowItem(row) {
            if (row.key == KEY_LOG_OUT) onLogOut() else row.onClick()
        }
    }
}

internal const val KEY_LOG_OUT = "logout"

@Composable
private fun MoreRowItem(row: MoreRow, onClick: () -> Unit) {
    val tint = if (row.destructive) LogoutRed else PitchGreen
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.12f), RowShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(row.icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(row.label),
                color = if (row.destructive) LogoutRed else DeepCarbon,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
            row.sub?.let { Text(it, color = DeepCarbon.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        // Log out doesn't open a screen, so it has no chevron.
        if (row.opensScreen) {
            Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null, tint = DeepCarbon.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ColumnScope.Version(account: MoreAccount?, onDark: Boolean = false) {
    val version = account?.appVersion ?: return
    Text(
        stringResource(Res.string.more_version, version),
        color = (if (onDark) Color.White else DeepCarbon).copy(alpha = 0.4f),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(vertical = 16.dp)
    )
}

/**
 * The coach's age-group picker (37c): "Create team" / "Add players" for one of the age groups the
 * action applies to. Only shown with two or more -- with one, it's preselected and skipped.
 */
@Composable
internal fun AgeGroupPickerDialog(
    title: StringResource,
    ageGroups: List<AgeGroup>,
    teamCount: (String) -> Int,
    onPick: (AgeGroup) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<AgeGroup?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = DeepCarbon,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                Text(stringResource(Res.string.more_pick_hint), color = DeepCarbon.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ageGroups.forEach { ag ->
                    val years = if (ag.startYear == ag.endYear) "${ag.endYear}" else "${ag.endYear}–${ag.startYear}"
                    Row(
                        Modifier.fillMaxWidth().clip(RowShape)
                            .selectable(selected = selected == ag, role = Role.RadioButton) { selected = ag }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == ag, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = PitchGreen))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(ag.name, color = DeepCarbon, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Text(pluralStringResource(Res.plurals.more_age_group_teams, teamCount(ag.id), years, teamCount(ag.id)), color = DeepCarbon.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onPick) }, enabled = selected != null) {
                Text(stringResource(Res.string.more_continue), color = if (selected != null) PitchGreen else DeepCarbon.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.btn_cancel), color = CarbonGray) } }
    )
}

/**
 * The panel's rows for this viewer (37a/c/d):
 * - CLUB: Players and Opponents for everyone; Club settings for admins/coordinators.
 * - ADD / CREATE: everything for admins/coordinators. For anyone else, only the actions the club's
 *   Permissions allow them in their own age groups (Create team / Add players, Coach level by
 *   default), each with its scope as a second line. Absent rows simply don't show.
 * - ACCOUNT: Account settings, Switch club (2+ clubs), Log out.
 * [onCreateTeam] / [onAddPlayers] get whether the action is scoped (a coach's) or club-wide.
 */
@Composable
internal fun moreGroups(
    details: com.ricardofrango.squadify.domain.usecase.ClubDetails,
    onPlayers: () -> Unit,
    onOpponents: () -> Unit,
    onClubSettings: () -> Unit,
    onAddSeason: () -> Unit,
    onCreateAgeGroup: () -> Unit,
    onCreateTeam: (scoped: Boolean) -> Unit,
    onAddPlayers: (scoped: Boolean) -> Unit,
    onNewStaff: () -> Unit,
    onAccount: () -> Unit,
    onSwitchClub: () -> Unit,
    clubCount: Int
): List<MoreGroup> {
    // Youngest first ("Sub-15, Sub-17"), the way the role chip reads.
    fun scope(ids: List<String>) = details.ageGroups.filter { it.id in ids }.sortedByDescending { it.endYear }.joinToString(", ") { it.name }.ifEmpty { null }
    val club = buildList {
        add(MoreRow("players", Res.drawable.ic_player, Res.string.more_players, onClick = onPlayers))
        add(MoreRow("opponents", Res.drawable.ic_teams, Res.string.more_opponents, onClick = onOpponents))
        if (details.canEditClub) add(MoreRow("settings", Res.drawable.ic_edit, Res.string.more_club_settings, stringResource(Res.string.more_club_settings_sub), onClick = onClubSettings))
    }
    val create = buildList {
        if (details.canEditClub) {
            add(MoreRow("season", Res.drawable.ic_calendar, Res.string.more_add_season, onClick = onAddSeason))
            add(MoreRow("ageGroup", Res.drawable.ic_age_group, Res.string.more_create_age_group, onClick = onCreateAgeGroup))
            add(MoreRow("team", Res.drawable.ic_teams, Res.string.more_create_team, onClick = { onCreateTeam(false) }))
            add(MoreRow("players+", Res.drawable.ic_player, Res.string.more_add_players, onClick = { onAddPlayers(false) }))
            add(MoreRow("staff", Res.drawable.ic_account, Res.string.more_new_staff, onClick = onNewStaff))
        } else {
            if (details.createTeamAgeGroupIds.isNotEmpty()) {
                add(MoreRow("team", Res.drawable.ic_teams, Res.string.more_create_team, scope(details.createTeamAgeGroupIds), onClick = { onCreateTeam(true) }))
            }
            if (details.createPlayersAgeGroupIds.isNotEmpty()) {
                add(MoreRow("players+", Res.drawable.ic_player, Res.string.more_add_players, scope(details.createPlayersAgeGroupIds), onClick = { onAddPlayers(true) }))
            }
        }
    }
    val account = buildList {
        add(MoreRow("account", Res.drawable.ic_account, Res.string.more_account_settings, stringResource(Res.string.more_account_settings_sub), onClick = onAccount))
        if (clubCount >= 2) add(MoreRow("switch", Res.drawable.ic_home, Res.string.more_switch_club, pluralStringResource(Res.plurals.more_clubs_count, clubCount, clubCount), onClick = onSwitchClub))
        add(MoreRow(KEY_LOG_OUT, Res.drawable.ic_logout, Res.string.more_log_out, destructive = true, opensScreen = false, onClick = {}))
    }
    return listOf(
        MoreGroup(Res.string.more_group_club, club),
        MoreGroup(Res.string.more_group_create, create),
        // Future sections (Training & planning) slot in here, between ADD / CREATE and ACCOUNT.
        MoreGroup(Res.string.more_group_account, account)
    ).filter { it.rows.isNotEmpty() }
}
