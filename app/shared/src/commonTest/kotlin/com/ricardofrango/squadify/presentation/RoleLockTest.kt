package com.ricardofrango.squadify.presentation

import com.ricardofrango.squadify.model.ClubMemberEntry
import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.presentation.ui.clubmember.RoleLock
import com.ricardofrango.squadify.presentation.ui.clubmember.roleLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

// The Member details role options (design 35) lock with the same rules the server enforces.
class RoleLockTest {
    private fun member(id: String, role: ClubRole) =
        ClubMemberEntry(id, "F$id", "L$id", "$id@x.pt", null, role, accountCreatedAt = Instant.parse("2026-01-01T00:00:00Z"))

    private fun overview(viewerRole: ClubRole, vararg members: ClubMemberEntry) =
        ClubMembersOverview(members.toList(), emptyList(), viewerUserId = members.first().userId, viewerRole = viewerRole)

    @Test
    fun theCurrentRoleIsNeverLocked() {
        val me = member("a", ClubRole.CLUB_ADMIN)
        assertNull(roleLock(overview(ClubRole.CLUB_ADMIN, me), me, ClubRole.CLUB_ADMIN))
    }

    @Test
    fun theOnlyAdminCannotBeDemoted() {
        val me = member("a", ClubRole.CLUB_ADMIN)
        val o = overview(ClubRole.CLUB_ADMIN, me, member("b", ClubRole.MEMBER))
        assertEquals(RoleLock.LAST_ADMIN, roleLock(o, me, ClubRole.COORDINATOR))
        assertEquals(RoleLock.LAST_ADMIN, roleLock(o, me, ClubRole.MEMBER))
    }

    @Test
    fun anAdminCanStepDownWhenAnotherAdminExists() {
        val me = member("a", ClubRole.CLUB_ADMIN)
        val o = overview(ClubRole.CLUB_ADMIN, me, member("b", ClubRole.CLUB_ADMIN))
        assertNull(roleLock(o, me, ClubRole.MEMBER))
    }

    @Test
    fun coordinatorsCanSwitchMemberAndCoordinatorButNotTouchAdmin() {
        val me = member("c", ClubRole.COORDINATOR)
        val target = member("m", ClubRole.MEMBER)
        val admin = member("a", ClubRole.CLUB_ADMIN)
        val o = overview(ClubRole.COORDINATOR, me, target, admin)
        assertNull(roleLock(o, target, ClubRole.COORDINATOR))
        assertEquals(RoleLock.ADMIN_ONLY, roleLock(o, target, ClubRole.CLUB_ADMIN))
        assertEquals(RoleLock.ADMIN_ONLY, roleLock(o, admin, ClubRole.MEMBER))
        assertEquals(RoleLock.ADMIN_ONLY, roleLock(o, me, ClubRole.CLUB_ADMIN))
    }
}
