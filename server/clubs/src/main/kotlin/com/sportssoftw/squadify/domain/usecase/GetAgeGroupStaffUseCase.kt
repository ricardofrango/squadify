package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.model.AgeGroupStaffMember
import com.sportssoftw.squadify.model.ClubRole
import com.sportssoftw.squadify.model.AgeGroupRole

class GetAgeGroupStaffUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): List<AgeGroupStaffMember> = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
            ?: throw NotFoundException("Age group not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = ageGroup.clubId,
            minClubRole = ClubRole.MEMBER,
            minAgeGroupRole = AgeGroupRole.STAFF,
            ageGroupId = ageGroupId,
            allowPlayerOrParentAccess = true
        )

        ageGroupRepository.getAgeGroupStaff(ageGroupId)
    }
}
