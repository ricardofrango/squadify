package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubPermissionRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.TeamRole
import com.ricardofrango.squadify.model.UserStatus
import com.ricardofrango.squadify.model.defaultMinClubRole
import com.ricardofrango.squadify.model.defaultMinTeamRole
import com.ricardofrango.squadify.model.isClubLevel

class CheckUserPermissionUseCase(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val playerRepository: PlayerRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val transactionManager: TransactionManager,
    private val clubPermissionRepository: ClubPermissionRepository
) {
    /**
     * Checks if a user has permission at the club or age-group level.
     * Platform Admins always have permission.
     *
     * @param userId The ID of the user performing the action.
     * @param clubId The ID of the club context.
     * @param minClubRole The minimum ClubRole required (e.g., COORDINATOR).
     * @param minTeamRole The minimum age-group staff role required (`age_group_staff`) once an
     *   age-group context is known -- either [ageGroupId] directly or [teamId] resolved to its age
     *   group. Coaches/staff attach to age groups now, not teams. Ignored when [configurablePermission]
     *   is given -- the two are mutually exclusive; a call site uses one or the other.
     * @param configurablePermission When set, [minTeamRole] is resolved from this club's own setting for
     *   the action instead of being a literal constant -- an explicit override if the club has one
     *   ([ClubPermissionRepository.getOverride], which may itself be `null` meaning "coordinator-only"),
     *   else [defaultMinTeamRole]. See `TECHNICAL_DECISIONS.md` on configurable permissions -- who may
     *   *change* these settings is never itself configurable (always plain `minClubRole = COORDINATOR`).
     *   A club-level action ([isClubLevel]) is different: its setting is a minimum **club** role, which
     *   replaces [minClubRole], and no age-group role ever passes it.
     * @param teamId Optional team ID; used only to derive its age group for the staff / player-link
     *   checks below.
     * @param ageGroupId Optional age-group ID for operations that act on an age group directly
     *   (adding players/staff, reading the pool). Takes precedence over [teamId].
     * @param allowPlayerOrParentAccess When true, also grants access if the caller is linked (via
     *   `UserPlayers`) to a player in this club (and, when an age-group context is given, specifically
     *   to a player in that age group's pool via `AgeGroupPlayers`). Defaults to false and must be
     *   explicitly opted into by READ use cases only -- see the DESIGN INVARIANT below.
     * @param errorMessage Optional custom error message.
     * @throws ForbiddenException if permission is denied.
     *
     * DESIGN INVARIANT (see "issues to fix.md" and PERMISSION_TEST_MATRIX.md): the player/parent path
     * below is opt-in via `allowPlayerOrParentAccess`, which defaults to false. No write/mutation use
     * case may ever pass `allowPlayerOrParentAccess = true` -- players and family members must never
     * gain write access. This is enforced by discipline + tests, not the type system: search for
     * `allowPlayerOrParentAccess = true` before adding it to any new call site and confirm the use case
     * is read-only.
     */
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        minClubRole: ClubRole? = null,
        minTeamRole: TeamRole? = null,
        configurablePermission: ConfigurablePermission? = null,
        teamId: String? = null,
        ageGroupId: String? = null,
        allowPlayerOrParentAccess: Boolean = false,
        errorMessage: String = "Insufficient permissions to perform this action"
    ) = transactionManager.runInTransaction {
        val user = userRepository.findById(userId) ?: throw ForbiddenException("User not found")

        // 0. A non-active account (suspended/inactive) is denied outright, even a platform admin's
        if (user.status != UserStatus.ACTIVE) throw ForbiddenException("User account is not active")

        // 1. Platform Admins have "God Mode"
        if (user.isPlatformAdmin) return@runInTransaction

        // A club-level configurable action: the club's setting is the minimum club role (admins and
        // coordinators by default, or admins only), and nothing else grants it.
        if (configurablePermission != null && configurablePermission.isClubLevel) {
            val required = clubPermissionRepository.getOverride(clubId, configurablePermission)?.minClubRole
                ?: configurablePermission.defaultMinClubRole!!
            val role = userRepository.getUserRoleInClub(userId, clubId)
            if (role != null && role.level >= required.level) return@runInTransaction
            throw ForbiddenException(errorMessage)
        }

        // The age-group context for the staff and player-link checks: given directly, or derived
        // from the team the operation targets.
        val effectiveAgeGroupId = ageGroupId
            ?: teamId?.let { teamRepository.getTeamById(it)?.ageGroupId }

        // 2. Check Club Level Role
        val userClubRole = userRepository.getUserRoleInClub(userId, clubId)

        if (userClubRole != null) {
            if (userClubRole == ClubRole.CLUB_ADMIN || userClubRole == ClubRole.COORDINATOR) {
                if (minClubRole == null || userClubRole.level >= minClubRole.level) return@runInTransaction
            }

            // If we only needed a club membership (MEMBER level 1)
            if (minClubRole != null && minClubRole == ClubRole.MEMBER && userClubRole.level >= ClubRole.MEMBER.level) {
                // For age-group-specific operations, we still need the age-group staff check below
                if (effectiveAgeGroupId == null) return@runInTransaction
            }
        }

        // 3. Check age-group staff role. When configurablePermission is given, its resolved threshold
        // replaces the literal minTeamRole entirely -- an override row that exists but stores `null`
        // means "coordinator-only" and must NOT fall back to the default (only a genuinely *missing*
        // row does), so this checks row presence explicitly rather than chaining `?:`.
        val effectiveMinTeamRole = if (configurablePermission != null) {
            val override = clubPermissionRepository.getOverride(clubId, configurablePermission)
            if (override != null) override.minTeamRole else configurablePermission.defaultMinTeamRole
        } else {
            minTeamRole
        }
        if (effectiveAgeGroupId != null && effectiveMinTeamRole != null) {
            val userAgeGroupRole = ageGroupRepository.getUserRoleInAgeGroup(userId, effectiveAgeGroupId)
            if (userAgeGroupRole != null && userAgeGroupRole.level >= effectiveMinTeamRole.level) {
                return@runInTransaction
            }
        }

        // 4. Check Player/Parent link via AgeGroupPlayers/UserPlayers -- READ-ONLY, opt-in only
        if (allowPlayerOrParentAccess) {
            val linkedPlayerIds = playerRepository.getPlayersByUser(userId)
                .filter { it.clubId == clubId }
                .map { it.id }
                .toSet()
            if (linkedPlayerIds.isNotEmpty()) {
                if (effectiveAgeGroupId == null) return@runInTransaction
                val poolPlayerIds = playerRepository.getPlayersByAgeGroup(effectiveAgeGroupId)
                    .map { it.id }
                    .toSet()
                if (linkedPlayerIds.any { it in poolPlayerIds }) return@runInTransaction
            }
        }

        throw ForbiddenException(errorMessage)
    }
}
