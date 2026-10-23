package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.InviteRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.ClubMemberEntry
import com.ricardofrango.squadify.model.ClubMembersOverview
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.MemberAgeGroupRole
import com.ricardofrango.squadify.model.MemberLinkedPlayer
import com.ricardofrango.squadify.model.PendingClubInvite

/**
 * Club settings → Members and Member details (designs 34, 35): every member with what they do in the
 * club -- club role, staff roles per age group, linked players -- plus the pending email invites.
 * Admins and coordinators only, like the rest of Club settings.
 */
class GetClubMembersOverviewUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val playerRepository: PlayerRepository,
    private val inviteRepository: InviteRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): ClubMembersOverview = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can view club members"
        )

        val seasons = seasonRepository.getSeasonsByClub(clubId).associateBy { it.id }
        val activeSeasonIds = seasons.values.filter { it.active }.map { it.id }.toSet()
        val ageGroups = ageGroupRepository.getAgeGroupsByClub(clubId)

        // Staff roles per user, current season first, then the age group's name.
        val rolesByUser = ageGroups.flatMap { ag ->
            ageGroupRepository.getAgeGroupStaff(ag.id).map { staff ->
                staff.userId to MemberAgeGroupRole(
                    ageGroupId = ag.id,
                    ageGroupName = ag.name,
                    seasonName = seasons[ag.seasonId]?.name.orEmpty(),
                    role = staff.role,
                    currentSeason = ag.seasonId in activeSeasonIds
                )
            }
        }.groupBy({ it.first }, { it.second })

        // A linked player's age group: the one in the active season if he's in it, else any.
        val ageGroupsById = ageGroups.associateBy { it.id }
        val poolByPlayer = playerRepository.getAgeGroupPlayersByClub(clubId).groupBy({ it.playerId }, { it.ageGroupId })
        fun playerAgeGroupName(playerId: String): String? {
            val groups = poolByPlayer[playerId].orEmpty().mapNotNull { ageGroupsById[it] }
            return (groups.firstOrNull { it.seasonId in activeSeasonIds } ?: groups.firstOrNull())?.name
        }
        val linksByUser = playerRepository.getUserPlayerLinksByClub(clubId).map { (linkedUserId, player, relationship) ->
            linkedUserId to MemberLinkedPlayer(
                playerId = player.id,
                name = "${player.firstName} ${player.lastName}",
                relationship = relationship,
                ageGroupName = playerAgeGroupName(player.id),
                shirtNumber = player.shirtNumber
            )
        }.groupBy({ it.first }, { it.second })

        val members = clubRepository.getClubMembers(clubId).mapNotNull { summary ->
            val user = userRepository.findById(summary.userId) ?: return@mapNotNull null
            ClubMemberEntry(
                userId = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                email = user.email,
                phone = user.phone,
                role = summary.role,
                ageGroupRoles = rolesByUser[user.id].orEmpty()
                    .sortedWith(compareByDescending<MemberAgeGroupRole> { it.currentSeason }.thenByDescending { it.seasonName }.thenBy { it.ageGroupName }),
                linkedPlayers = linksByUser[user.id].orEmpty().sortedBy { it.name },
                accountCreatedAt = user.createdAt,
                avatarUrl = user.avatarUrl
            )
        }

        ClubMembersOverview(
            members = members,
            pendingInvites = inviteRepository.findClubMemberInvites(clubId).map {
                PendingClubInvite(it.id, it.email, it.role ?: ClubRole.MEMBER, it.createdAt)
            },
            viewerUserId = userId,
            viewerRole = userRepository.getUserRoleInClub(userId, clubId)
                ?: if (userRepository.findById(userId)?.isPlatformAdmin == true) ClubRole.CLUB_ADMIN else ClubRole.MEMBER
        )
    }
}
