package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.footballmanager.model.AgeGroup
import com.ricardofrango.footballmanager.model.Role

class GetAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, clubId: String): List<AgeGroup> {
        // 1. Check permissions first (Fail fast)
        checkUserPermissionUseCase(userId, clubId, Role.PLAYER)

        return ageGroupRepository.getAgeGroupsByClub(clubId)
    }
}
