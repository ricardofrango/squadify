package com.sportssoftw.squadify.domain.repository

import com.sportssoftw.squadify.model.CreateTeamsRequest
import com.sportssoftw.squadify.model.Team
import com.sportssoftw.squadify.model.TeamDetails
import com.sportssoftw.squadify.model.TeamSeasonRecord

interface TeamRepository {
    suspend fun getTeams(clubId: String): List<Team>
    suspend fun getTeamDetails(teamId: String): TeamDetails
    suspend fun getTeamSeasonRecord(teamId: String): TeamSeasonRecord
    suspend fun getTeamFullStats(teamId: String): com.sportssoftw.squadify.model.FullStatsData
    suspend fun createTeams(request: CreateTeamsRequest): List<Team>
    suspend fun updateTeam(teamId: String, request: com.sportssoftw.squadify.model.UpdateTeamRequest)
    // Deletes the team and all its matches (`DELETE /teams/{id}`).
    suspend fun deleteTeam(teamId: String)
}
