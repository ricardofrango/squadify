package com.ricardofrango.squadify.presentation.ui.editclub

import com.ricardofrango.squadify.presentation.ui.components.FilterChipRow
import com.ricardofrango.squadify.presentation.ui.theme.ClubChrome
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ricardofrango.squadify.model.ClubMemberEntry
import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.PendingClubInvite
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.presentation.ui.components.PersonPhoto
import com.ricardofrango.squadify.presentation.ui.components.StatsCard
import com.ricardofrango.squadify.presentation.ui.components.StatsHeading
import com.ricardofrango.squadify.presentation.ui.components.StatsMessage
import com.ricardofrango.squadify.presentation.ui.components.StatsSecondaryText
import com.ricardofrango.squadify.presentation.ui.theme.CarbonGray
import com.ricardofrango.squadify.presentation.ui.theme.DeepCarbon
import com.ricardofrango.squadify.presentation.ui.theme.PitchGreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.ic_chevron_right
import squadify.app.shared.generated.resources.ic_more
import squadify.app.shared.generated.resources.label_role_coordinator
import squadify.app.shared.generated.resources.label_role_member
import squadify.app.shared.generated.resources.members_cancel_invite
import squadify.app.shared.generated.resources.members_count
import squadify.app.shared.generated.resources.members_filter_admins
import squadify.app.shared.generated.resources.members_filter_all
import squadify.app.shared.generated.resources.members_filter_coordinators
import squadify.app.shared.generated.resources.members_filter_members
import squadify.app.shared.generated.resources.members_filter_pending
import squadify.app.shared.generated.resources.members_group_pending
import squadify.app.shared.generated.resources.members_invite_short
import squadify.app.shared.generated.resources.members_invite_title
import squadify.app.shared.generated.resources.members_invited_days
import squadify.app.shared.generated.resources.members_invited_today
import squadify.app.shared.generated.resources.members_line_coach
import squadify.app.shared.generated.resources.members_line_coordinator_only
import squadify.app.shared.generated.resources.members_line_parent
import squadify.app.shared.generated.resources.members_line_staff
import squadify.app.shared.generated.resources.members_load_failed_body
import squadify.app.shared.generated.resources.members_load_failed_title
import squadify.app.shared.generated.resources.members_no_match_body
import squadify.app.shared.generated.resources.members_no_match_title
import squadify.app.shared.generated.resources.members_only_you_body
import squadify.app.shared.generated.resources.members_only_you_title
import squadify.app.shared.generated.resources.members_resend
import squadify.app.shared.generated.resources.members_role_admin
import squadify.app.shared.generated.resources.members_search
import squadify.app.shared.generated.resources.members_you
import kotlin.time.Clock

// The Members tab of Club settings (design 34): everyone in the club grouped by club role, with
// search, a role filter, and the pending invites (Resend, ⋮ → Cancel invite).

fun ClubRole.labelRes(): StringResource = when (this) {
    ClubRole.CLUB_ADMIN -> Res.string.members_role_admin
    ClubRole.COORDINATOR -> Res.string.label_role_coordinator
    ClubRole.MEMBER -> Res.string.label_role_member
}

private fun MembersFilter.labelRes() = when (this) {
    MembersFilter.ALL -> Res.string.members_filter_all
    MembersFilter.ADMINS -> Res.string.members_filter_admins
    MembersFilter.COORDINATORS -> Res.string.members_filter_coordinators
    MembersFilter.MEMBERS -> Res.string.members_filter_members
    MembersFilter.PENDING -> Res.string.members_filter_pending
}

private fun MembersFilter.role(): ClubRole? = when (this) {
    MembersFilter.ADMINS -> ClubRole.CLUB_ADMIN
    MembersFilter.COORDINATORS -> ClubRole.COORDINATOR
    MembersFilter.MEMBERS -> ClubRole.MEMBER
    else -> null
}

/** Search matches first name, last name and email (and "first last"), case-insensitively. */
internal fun ClubMemberEntry.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return "$firstName $lastName".contains(q, ignoreCase = true) || email.contains(q, ignoreCase = true)
}

internal fun ClubMemberEntry.initials(): String =
    listOf(firstName, lastName).mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "?" }

/**
 * The one line under a member's name -- what they do in the club: their coach or staff age groups this
 * season, the players they're a parent of, "Coordinator only", or else their email.
 */
@Composable
internal fun memberLine(member: ClubMemberEntry): String {
    val current = member.ageGroupRoles.filter { it.currentSeason }
    val coach = current.filter { it.role == AgeGroupRole.COACH }.map { it.ageGroupName }.distinct()
    val staff = current.filter { it.role == AgeGroupRole.STAFF }.map { it.ageGroupName }.distinct()
    return when {
        coach.isNotEmpty() -> stringResource(Res.string.members_line_coach, coach.joinToString(", "))
        staff.isNotEmpty() -> stringResource(Res.string.members_line_staff, staff.joinToString(", "))
        member.linkedPlayers.isNotEmpty() -> stringResource(Res.string.members_line_parent, member.linkedPlayers.joinToString(", ") { it.name })
        member.role == ClubRole.COORDINATOR -> stringResource(Res.string.members_line_coordinator_only)
        else -> member.email
    }
}

/** "Invited 3 days ago · Member" / "Invited today · Member". */
@Composable
internal fun invitedLine(invite: PendingClubInvite): String {
    val days = ((Clock.System.now() - invite.invitedAt).inWholeDays).toInt().coerceAtLeast(0)
    val role = stringResource(invite.role.labelRes())
    return if (days == 0) stringResource(Res.string.members_invited_today, role)
    else pluralStringResource(Res.plurals.members_invited_days, days, days, role)
}

@Composable
internal fun ClubMembersTab(
    state: MembersUiState,
    filter: MembersFilter,
    query: String,
    // Landscape (34d): the filter becomes a vertical list with counts beside the search.
    sideBySide: Boolean,
    modifier: Modifier,
    onFilterChange: (MembersFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onInvite: () -> Unit,
    onRetry: () -> Unit,
    onOpenMember: (ClubMemberEntry) -> Unit,
    onOpenInvite: (PendingClubInvite) -> Unit,
    onResend: (PendingClubInvite) -> Unit,
    onCancelInvite: (PendingClubInvite) -> Unit
) {
    val padding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp)
    when (state) {
        MembersUiState.Loading -> LazyColumn(modifier, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Heading(count = null, onInvite = onInvite) }
            item { SearchField(query, onQueryChange) }
            item { SkeletonCard() }
        }

        MembersUiState.Error -> LazyColumn(modifier, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                StatsCard(contentPadding = 16.dp) {
                    StatsMessage(stringResource(Res.string.members_load_failed_title), stringResource(Res.string.members_load_failed_body), onRetry = onRetry)
                }
            }
        }

        is MembersUiState.Loaded -> {
            val overview = state.overview
            if (sideBySide) {
                Row(modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Heading(overview.members.size, onInvite)
                        SearchField(query, onQueryChange)
                        FilterList(overview, filter, onFilterChange)
                    }
                    MemberList(
                        overview, filter, query, Modifier.weight(1f).fillMaxHeight(), PaddingValues(bottom = 20.dp), header = {},
                        onInvite, onOpenMember, onOpenInvite, onResend, onCancelInvite
                    )
                }
            } else {
                MemberList(
                    overview, filter, query, modifier, padding,
                    header = {
                        item { Heading(overview.members.size, onInvite) }
                        // Nothing to search or filter when you're on your own.
                        if (overview.members.size + overview.pendingInvites.size > 1) {
                            item { SearchField(query, onQueryChange) }
                            item { FilterChips(overview, filter, onFilterChange) }
                        }
                    },
                    onInvite, onOpenMember, onOpenInvite, onResend, onCancelInvite
                )
            }
        }
    }
}

@Composable
private fun MemberList(
    overview: ClubMembersOverview,
    filter: MembersFilter,
    query: String,
    modifier: Modifier,
    padding: PaddingValues,
    header: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
    onInvite: () -> Unit,
    onOpenMember: (ClubMemberEntry) -> Unit,
    onOpenInvite: (PendingClubInvite) -> Unit,
    onResend: (PendingClubInvite) -> Unit,
    onCancelInvite: (PendingClubInvite) -> Unit
) {
    val members = overview.members.filter { it.matches(query) }
    val invites = overview.pendingInvites.filter { query.isBlank() || it.email.contains(query.trim(), ignoreCase = true) }
    val roleGroups = listOf(ClubRole.CLUB_ADMIN, ClubRole.COORDINATOR, ClubRole.MEMBER)
        .filter { filter == MembersFilter.ALL || filter.role() == it }
        .map { role -> role to members.filter { it.role == role }.sortedWith(compareBy({ it.firstName.lowercase() }, { it.lastName.lowercase() })) }
        .filter { it.second.isNotEmpty() }
    val showInvites = (filter == MembersFilter.ALL || filter == MembersFilter.PENDING) && invites.isNotEmpty()
    val onlyYou = overview.members.size <= 1 && overview.pendingInvites.isEmpty()

    LazyColumn(modifier, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        header()
        roleGroups.forEach { (role, group) ->
            item(key = "h-$role") { StatsHeading("${stringResource(role.groupRes())} · ${group.size}", Modifier.padding(start = 2.dp, top = 4.dp)) }
            item(key = "g-$role") {
                StatsCard(contentPadding = 0.dp) {
                    group.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
                        MemberRow(m, isYou = m.userId == overview.viewerUserId, onClick = { onOpenMember(m) })
                    }
                }
            }
        }
        if (showInvites) {
            item(key = "h-pending") { StatsHeading("${stringResource(Res.string.members_group_pending)} · ${invites.size}", Modifier.padding(start = 2.dp, top = 4.dp)) }
            item(key = "g-pending") {
                StatsCard(contentPadding = 0.dp) {
                    invites.forEachIndexed { i, invite ->
                        if (i > 0) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
                        InviteRow(invite, onClick = { onOpenInvite(invite) }, onResend = { onResend(invite) }, onCancel = { onCancelInvite(invite) })
                    }
                }
            }
        }
        when {
            onlyYou -> item {
                EmptyCard(stringResource(Res.string.members_only_you_title), stringResource(Res.string.members_only_you_body), stringResource(Res.string.members_invite_title), filled = true, onInvite)
            }
            roleGroups.isEmpty() && !showInvites && query.isNotBlank() -> item {
                EmptyCard(stringResource(Res.string.members_no_match_title, query.trim()), stringResource(Res.string.members_no_match_body), stringResource(Res.string.members_invite_title), filled = false, onInvite)
            }
        }
    }
}

private fun ClubRole.groupRes() = when (this) {
    ClubRole.CLUB_ADMIN -> Res.string.members_filter_admins
    ClubRole.COORDINATOR -> Res.string.members_filter_coordinators
    ClubRole.MEMBER -> Res.string.members_filter_members
}

private fun ClubMembersOverview.count(filter: MembersFilter) = when (filter) {
    MembersFilter.ALL -> members.size + pendingInvites.size
    MembersFilter.PENDING -> pendingInvites.size
    else -> members.count { it.role == filter.role() }
}

@Composable
private fun Heading(count: Int?, onInvite: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        StatsHeading(
            if (count != null) pluralStringResource(Res.plurals.members_count, count, count) else "— ${stringResource(Res.string.members_filter_members)}",
            Modifier.weight(1f)
        )
        Text(
            stringResource(Res.string.members_invite_short),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
            color = PitchGreen,
            modifier = Modifier.clickable(onClick = onInvite)
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(Res.string.members_search)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DeepCarbon, unfocusedTextColor = DeepCarbon,
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            focusedBorderColor = PitchGreen, unfocusedBorderColor = CarbonGray.copy(alpha = 0.2f)
        )
    )
}

/** All · Admins · Coordinators · Members · Pending · n -- scrolls sideways (34c). */
@Composable
private fun FilterChips(overview: ClubMembersOverview, selected: MembersFilter, onSelect: (MembersFilter) -> Unit) {
    FilterChipRow(
        options = MembersFilter.entries.filter { it != MembersFilter.PENDING || overview.pendingInvites.isNotEmpty() },
        selected = selected,
        label = { f -> stringResource(f.labelRes()).let { if (f == MembersFilter.PENDING) "$it · ${overview.pendingInvites.size}" else it } },
        onSelect = onSelect,
        insidePaddedContent = true
    )
}

/** Landscape: the filter as a vertical list with counts (34d). */
@Composable
private fun FilterList(overview: ClubMembersOverview, selected: MembersFilter, onSelect: (MembersFilter) -> Unit) {
    StatsCard(contentPadding = 6.dp) {
        MembersFilter.entries.forEach { f ->
            val isSelected = f == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) ClubChrome else Color.White)
                    .clickable { onSelect(f) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    stringResource(f.labelRes()),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) Color.White else DeepCarbon
                )
                Text(
                    overview.count(f).toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) Color.White.copy(alpha = 0.7f) else StatsSecondaryText
                )
            }
        }
    }
}

@Composable
internal fun MemberAvatar(initials: String, size: Int = 36, photoUrl: String? = null) {
    // Their photo when they've set one; the initials circle otherwise, as before.
    if (photoUrl != null) {
        PersonPhoto(photoUrl, initials, size.dp, PitchGreen.copy(alpha = 0.15f), PitchGreen)
        return
    }
    Box(Modifier.size(size.dp).background(PitchGreen.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = (size * 0.36f).sp), color = PitchGreen)
    }
}

@Composable
private fun MemberRow(member: ClubMemberEntry, isYou: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        MemberAvatar(member.initials(), photoUrl = member.avatarUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    append("${member.firstName} ${member.lastName}")
                    if (isYou) append(" ").append(stringResource(Res.string.members_you))
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp),
                color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(memberLine(member), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = StatsSecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null, tint = DeepCarbon.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InviteRow(invite: PendingClubInvite, onClick: () -> Unit, onResend: () -> Unit, onCancel: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(CarbonGray.copy(alpha = 0.07f), CircleShape), contentAlignment = Alignment.Center) {
            Text("@", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = StatsSecondaryText)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(invite.email, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = DeepCarbon, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(invitedLine(invite), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = StatsSecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(Res.string.members_resend),
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onResend).padding(horizontal = 6.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
            color = PitchGreen
        )
        Box {
            Box(Modifier.size(width = 32.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).clickable { menu = true }, contentAlignment = Alignment.Center) {
                Icon(painterResource(Res.drawable.ic_more), contentDescription = stringResource(Res.string.members_cancel_invite), tint = DeepCarbon.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(Color.White)) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.members_cancel_invite), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onCancel() }
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String, actionLabel: String, filled: Boolean, onAction: () -> Unit) {
    StatsCard(contentPadding = 16.dp) {
        StatsMessage(title, body)
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = onAction,
            shape = RoundedCornerShape(12.dp),
            color = if (filled) PitchGreen else Color.White,
            border = if (filled) null else BorderStroke(1.5.dp, CarbonGray.copy(alpha = 0.2f))
        ) {
            Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(actionLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), color = if (filled) Color.White else CarbonGray)
            }
        }
    }
}

/** Grey placeholder rows while the list loads (34b). */
@Composable
private fun SkeletonCard() {
    StatsCard(contentPadding = 0.dp) {
        repeat(4) { i ->
            if (i > 0) HorizontalDivider(color = CarbonGray.copy(alpha = 0.07f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(CarbonGray.copy(alpha = 0.08f), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(140.dp).height(12.dp).background(CarbonGray.copy(alpha = 0.08f), RoundedCornerShape(4.dp)))
                    Box(Modifier.width(90.dp).height(10.dp).background(CarbonGray.copy(alpha = 0.06f), RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}
