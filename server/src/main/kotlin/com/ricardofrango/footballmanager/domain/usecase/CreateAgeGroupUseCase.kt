package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.CreateAgeGroupRequest
import com.ricardofrango.footballmanager.model.Role
import kotlin.uuid.Uuid

class CreateAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, request: CreateAgeGroupRequest): Boolean {
        val userRole = userRepository.getUserRoleInClub(userId, request.clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
            throw ForbiddenException("Only admins or coordinators can create age groups")
        }

        if (request.name.isBlank()) {
            throw ValidationException("Age group name cannot be empty")
        }

        val ageGroup = AgeGroup(
            id = Uuid.random().toString(),
            clubId = request.clubId,
            name = request.name,
            seasonId = request.seasonId,
            sortOrder = request.sortOrder
        )

        return ageGroupRepository.createAgeGroup(ageGroup)
    }
}
