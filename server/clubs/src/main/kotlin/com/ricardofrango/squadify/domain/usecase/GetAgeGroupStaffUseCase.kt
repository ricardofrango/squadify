package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroupStaffMember
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.AgeGroupRole

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
