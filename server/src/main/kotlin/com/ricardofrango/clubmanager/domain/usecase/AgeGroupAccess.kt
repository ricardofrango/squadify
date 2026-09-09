package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.model.AgeGroup

/**
 * The age groups in [clubId] a non-admin caller can see: those they're staff on (`age_group_staff`),
 * plus those a player they're linked to (via `user_players`) sits in the pool of. Used by the
 * "narrow a plain member / coach / parent down to their own corner of the club" branch of
 * `GetAgeGroupsUseCase` / `GetSeasonsUseCase` / `GetMatchesUseCase`. Admins/coordinators bypass this.
 */
internal suspend fun visibleAgeGroups(
    playerRepository: PlayerRepository,
    ageGroupRepository: AgeGroupRepository,
    userId: String,
    clubId: String
): List<AgeGroup> {
    val linkedPlayerIds = playerRepository.getPlayersByUser(userId)
        .filter { it.clubId == clubId }
        .map { it.id }
        .toSet()
    val linkedAgeGroupIds = if (linkedPlayerIds.isEmpty()) {
        emptySet()
    } else {
        playerRepository.getAgeGroupPlayersByClub(clubId)
            .filter { it.playerId in linkedPlayerIds }
            .map { it.ageGroupId }
            .toSet()
    }

    return ageGroupRepository.getAgeGroupsByClub(clubId).filter { ag ->
        ageGroupRepository.getUserRoleInAgeGroup(userId, ag.id) != null || ag.id in linkedAgeGroupIds
    }
}
