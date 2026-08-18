package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.Role

class CreateAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, ageGroup: AgeGroup): Boolean {
        val userRole = userRepository.getUserRoleInClub(userId, ageGroup.clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
            throw ForbiddenException("Only admins or coordinators can create age groups")
        }

        if (ageGroup.name.isBlank()) {
            throw ValidationException("Age group name cannot be empty")
        }

        return ageGroupRepository.createAgeGroup(ageGroup)
    }
}
