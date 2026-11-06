package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole

/**
 * `DELETE /age-groups/{ageGroupId}/staff/{userId}`: takes a coach or staff member off an age group
 * (the developer, 2026-11-06), from the age group's Members tab or the member's details. Whoever can
 * add staff can remove them: club admins and coordinators (AddStaffToAgeGroupUseCase). They stay in the
 * club with their club role; only Remove from club (RemoveClubMemberUseCase) takes that away.
 */
class RemoveStaffFromAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val transactionManager: TransactionManager,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, ageGroupId: String, staffUserId: String) = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
        checkUserPermissionUseCase(
            userId = userId,
            clubId = ageGroup.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can remove staff from age groups"
        )
        if (!ageGroupRepository.removeStaffFromAgeGroup(ageGroupId, staffUserId)) {
            throw NotFoundException("Not staff on this age group")
        }
    }
}
