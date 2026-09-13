package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*
import kotlin.uuid.Uuid

class AddStaffToAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        ageGroupId: String,
        request: AddStaffToAgeGroupRequest
    ): Boolean = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
            ?: throw ValidationException("Age group not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = ageGroup.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can add staff to age groups"
        )

        val staffUserIds = request.staff.map { it.userId }
        val userRolesMap = userRepository.getUserRolesInClub(staffUserIds, ageGroup.clubId)

        val staffToCreate = request.staff.map { staffItem ->
            userRolesMap[staffItem.userId]
                ?: throw ForbiddenException("User ${staffItem.userId} must be a member of the club before being added to an age group")

            AgeGroupStaff(
                id = Uuid.random().toString(),
                ageGroupId = ageGroupId,
                userId = staffItem.userId,
                role = staffItem.role
            )
        }

        ageGroupRepository.addStaffToAgeGroup(staffToCreate)
    }
}
