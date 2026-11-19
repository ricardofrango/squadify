package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.TeamRepository
import com.sportssoftw.squadify.model.FullStatsData

/** The Full stats table's data, for a team (only who played for it) or a whole age group. */
class GetFullStatsUseCase(
    private val teamRepository: TeamRepository,
    private val ageGroupRepository: AgeGroupRepository
) {
    suspend fun team(teamId: String): FullStatsData = teamRepository.getTeamFullStats(teamId)
    suspend fun ageGroup(ageGroupId: String): FullStatsData = ageGroupRepository.getAgeGroupFullStats(ageGroupId)
}
