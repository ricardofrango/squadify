package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.model.Team

class GetTeamsUseCase(
    private val teamRepository: TeamRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Team> = transactionManager.runInTransaction {
        accessPolicy.requireViewClub(userId, clubId)

        // The teams of the age groups the caller is attached to, unless they see the whole club.
        val visible = accessPolicy.visibleAgeGroupIds(userId, clubId)
        teamRepository.getTeamsByClub(clubId).filter { visible == null || it.ageGroupId in visible }
    }
}
