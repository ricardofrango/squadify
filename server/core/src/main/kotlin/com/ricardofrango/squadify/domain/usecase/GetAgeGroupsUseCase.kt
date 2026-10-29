package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AgeGroup

class GetAgeGroupsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<AgeGroup> = transactionManager.runInTransaction {
        accessPolicy.requireViewClub(userId, clubId)

        // Narrowed to what the caller is attached to, unless they see the whole club.
        val visible = accessPolicy.visibleAgeGroupIds(userId, clubId)
        ageGroupRepository.getAgeGroupsByClub(clubId).filter { visible == null || it.id in visible }
    }
}
