package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubDetailsResponse
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.ViewerAgeGroup

class GetClubDetailsUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val userRepository: UserRepository,
    private val seasonRepository: SeasonRepository,
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend operator fun invoke(userId: String, clubId: String): ClubDetailsResponse = transactionManager.runInTransaction {
        // Everyone who is a member of the club (Member level 1) can see details
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.MEMBER,
            allowPlayerOrParentAccess = true,
            errorMessage = "You do not have access to this club"
        )

        val club = clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")

        // Seasons and age groups are both created by COORDINATOR+ (see CreateSeasonUseCase and
        // CreateAgeGroupsUseCase -- identical gate in both). One non-throwing probe covers both fields
        // rather than repeating the same permission check twice; it's still two separate response
        // fields so either could diverge later without an API shape change. Lets the client tell an
        // admin/coordinator "nothing here yet, create one" apart from a plain member's "nothing here yet".
        val canEditClub = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.COORDINATOR,
                errorMessage = "Only admins or coordinators can manage seasons and age groups"
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        // Disabling / reactivating players: the club-level MANAGE_PLAYER_STATUS setting.
        val canManagePlayerStatus = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.MANAGE_PLAYER_STATUS
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        val viewerRole = userRepository.getUserRoleInClub(userId, clubId)
            ?: if (userRepository.findById(userId)?.isPlatformAdmin == true) ClubRole.CLUB_ADMIN else null

        // The viewer's coach/staff age groups in the active season (or the latest, if none is marked
        // active), for the "Coach · Sub-15, Sub-17" chip and the scoped create actions below.
        val seasons = seasonRepository.getSeasonsByClub(clubId)
        val season = seasons.firstOrNull { it.active } ?: seasons.maxByOrNull { it.endDate }
        val viewerAgeGroups = season?.let { ageGroupRepository.findByClubAndSeason(clubId, it.id) }.orEmpty()
            .sortedByDescending { it.endYear } // youngest first: "Coach · Sub-15, Sub-17"
            .mapNotNull { ag -> ageGroupRepository.getUserRoleInAgeGroup(userId, ag.id)?.let { ViewerAgeGroup(ag.id, ag.name, it) } }

        // Where a non-coordinator may create teams / add players: the same probes CreateTeamsUseCase and
        // CreatePlayersUseCase run, per age group the viewer is on, so the menu matches the server.
        suspend fun allowedFor(permission: ConfigurablePermission): List<String> =
            if (canEditClub) emptyList()
            else viewerAgeGroups.filter { ag ->
                try {
                    checkUserPermissionUseCase(
                        userId = userId,
                        clubId = clubId,
                        minClubRole = ClubRole.COORDINATOR,
                        configurablePermission = permission,
                        ageGroupId = ag.ageGroupId
                    )
                    true
                } catch (e: ForbiddenException) {
                    false
                }
            }.map { it.ageGroupId }

        ClubDetailsResponse(
            club = club,
            canEditClub = canEditClub,
            canManagePlayerStatus = canManagePlayerStatus,
            viewerRole = viewerRole,
            viewerAgeGroups = viewerAgeGroups,
            createTeamAgeGroupIds = allowedFor(ConfigurablePermission.CREATE_TEAM),
            createPlayersAgeGroupIds = allowedFor(ConfigurablePermission.CREATE_PLAYERS)
        )
    }
}
