package com.ricardofrango.squadify.coach.access

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.PushAudience
import com.ricardofrango.squadify.model.ClubRole

/**
 * The coach server's: an age group's games, teams and ready call-ups go to the space's owner and that
 * age group's staff, not to staff on its other age groups (the developer, 2026-10-31).
 */
class CoachPushAudience(
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository
) : PushAudience {
    override suspend fun forGames(clubId: String, ageGroupId: String): List<String> = ownerAndStaff(clubId, ageGroupId)

    override suspend fun forCallUps(clubId: String, ageGroupId: String): List<String> = ownerAndStaff(clubId, ageGroupId)

    private suspend fun ownerAndStaff(clubId: String, ageGroupId: String): List<String> {
        val owners = clubRepository.getClubMembers(clubId).filter { it.role == ClubRole.CLUB_ADMIN }.map { it.userId }
        return (owners + ageGroupRepository.getAgeGroupStaff(ageGroupId).map { it.userId }).distinct()
    }
}
