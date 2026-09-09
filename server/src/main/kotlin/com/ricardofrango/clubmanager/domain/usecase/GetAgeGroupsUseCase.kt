package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.repository.UserRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.model.AgeGroup
import com.ricardofrango.clubmanager.model.ClubRole

class GetAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val userRepository: UserRepository,
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<AgeGroup> = transactionManager.runInTransaction {
        // 1. Check permissions first (Member of club, or a parent/player linked to someone in this club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER, allowPlayerOrParentAccess = true)

        // 2. Club admins/coordinators see every age group. Everyone else -- plain member, coach/staff,
        //    player, parent -- only sees the age group(s) they're attached to (an AgeGroupStaff
        //    assignment or a linked player in that age group's pool).
        val user = userRepository.findById(userId)
        val userClubRole = userRepository.getUserRoleInClub(userId, clubId)
        val seesEverything = user?.isPlatformAdmin == true ||
            userClubRole == ClubRole.CLUB_ADMIN ||
            userClubRole == ClubRole.COORDINATOR

        if (seesEverything) {
            ageGroupRepository.getAgeGroupsByClub(clubId)
        } else {
            visibleAgeGroups(playerRepository, ageGroupRepository, userId, clubId)
        }
    }
}
