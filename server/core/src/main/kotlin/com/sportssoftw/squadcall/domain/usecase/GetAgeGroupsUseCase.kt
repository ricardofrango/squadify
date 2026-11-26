package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.model.AgeGroup

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
