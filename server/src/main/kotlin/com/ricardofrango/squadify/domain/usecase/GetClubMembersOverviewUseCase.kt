package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubMemberAccount
import com.ricardofrango.squadify.domain.repository.MemberListFilter
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
import com.ricardofrango.squadify.model.SearchText

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
        checkAccess(userId, clubId)
        val members = clubRepository.getClubMembersPage(clubId, MemberListFilter(), Int.MAX_VALUE, 0)
        overview(userId, clubId, members)
    }

    /**
     * One page of the Members tab (`GET /clubs/{clubId}/members/overview` with `limit`): [role] and
     * [search] (name or email) narrow it here rather than on the device, admins first, then
     * coordinators, then members, each by name. [memberUserId] asks for that one member (Member
     * details). The pending invites always come whole, and the role counts cover the whole club.
     */
    suspend fun page(
        userId: String,
        clubId: String,
        role: ClubRole?,
        search: String?,
        memberUserId: String?,
        limit: Int,
        offset: Int
    ): ClubMembersOverview = transactionManager.runInTransaction {
        checkAccess(userId, clubId)
        val folded = search?.trim()?.let(SearchText::key)?.takeIf { it.isNotEmpty() }
        // One extra row says whether another page follows.
        val rows = clubRepository.getClubMembersPage(clubId, MemberListFilter(role, folded, memberUserId), limit + 1, offset)
        val roleCounts = clubRepository.countClubMembersByRole(clubId)
        overview(userId, clubId, rows.take(limit)).copy(
            roleCounts = roleCounts,
            matchingRoleCounts = if (folded == null) roleCounts else clubRepository.countClubMembersByRole(clubId, folded),
            hasMore = rows.size > limit
        )
    }

    private suspend fun checkAccess(userId: String, clubId: String) {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can view club members"
        )
    }

    // [members] with what each does in the club, plus the pending invites and the viewer.
    private suspend fun overview(userId: String, clubId: String, members: List<ClubMemberAccount>): ClubMembersOverview {
        val seasons = seasonRepository.getSeasonsByClub(clubId).associateBy { it.id }
        val activeSeasonIds = seasons.values.filter { it.active }.map { it.id }.toSet()
        val ageGroups = ageGroupRepository.getAgeGroupsByClub(clubId)
        val memberIds = members.map { it.userId }.toSet()

        // Staff roles per user, current season first, then the age group's name.
        val rolesByUser = ageGroups.flatMap { ag ->
            ageGroupRepository.getAgeGroupStaff(ag.id).filter { it.userId in memberIds }.map { staff ->
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
        val links = playerRepository.getUserPlayerLinksByClub(clubId).filter { it.first in memberIds }
        val poolByPlayer = if (links.isEmpty()) emptyMap()
        else playerRepository.getAgeGroupPlayersByClub(clubId).groupBy({ it.playerId }, { it.ageGroupId })
        fun playerAgeGroupName(playerId: String): String? {
            val groups = poolByPlayer[playerId].orEmpty().mapNotNull { ageGroupsById[it] }
            return (groups.firstOrNull { it.seasonId in activeSeasonIds } ?: groups.firstOrNull())?.name
        }
        val linksByUser = links.map { (linkedUserId, player, relationship) ->
            linkedUserId to MemberLinkedPlayer(
                playerId = player.id,
                name = "${player.firstName} ${player.lastName}",
                relationship = relationship,
                ageGroupName = playerAgeGroupName(player.id),
                shirtNumber = player.shirtNumber
            )
        }.groupBy({ it.first }, { it.second })

        return ClubMembersOverview(
            members = members.map { member ->
                ClubMemberEntry(
                    userId = member.userId,
                    firstName = member.firstName,
                    lastName = member.lastName,
                    email = member.email,
                    phone = member.phone,
                    role = member.role,
                    ageGroupRoles = rolesByUser[member.userId].orEmpty()
                        .sortedWith(compareByDescending<MemberAgeGroupRole> { it.currentSeason }.thenByDescending { it.seasonName }.thenBy { it.ageGroupName }),
                    linkedPlayers = linksByUser[member.userId].orEmpty().sortedBy { it.name },
                    accountCreatedAt = member.accountCreatedAt,
                    avatarUrl = member.avatarUrl
                )
            },
            pendingInvites = inviteRepository.findClubMemberInvites(clubId).map {
                PendingClubInvite(it.id, it.email, it.role ?: ClubRole.MEMBER, it.createdAt)
            },
            viewerUserId = userId,
            viewerRole = userRepository.getUserRoleInClub(userId, clubId)
                ?: if (userRepository.findById(userId)?.isPlatformAdmin == true) ClubRole.CLUB_ADMIN else ClubRole.MEMBER
        )
    }
}
