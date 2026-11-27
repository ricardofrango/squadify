package com.sportssoftw.squadcall.domain.repository

import com.sportssoftw.squadcall.model.CreateTeamsRequest
import com.sportssoftw.squadcall.model.Team
import com.sportssoftw.squadcall.model.TeamDetails
import com.sportssoftw.squadcall.model.TeamSeasonRecord

interface TeamRepository {
    suspend fun getTeams(clubId: String): List<Team>
    suspend fun getTeamDetails(teamId: String): TeamDetails
    suspend fun getTeamSeasonRecord(teamId: String): TeamSeasonRecord
    suspend fun getTeamFullStats(teamId: String): com.sportssoftw.squadcall.model.FullStatsData
    suspend fun createTeams(request: CreateTeamsRequest): List<Team>
    suspend fun updateTeam(teamId: String, request: com.sportssoftw.squadcall.model.UpdateTeamRequest)
    // Deletes the team and all its matches (`DELETE /teams/{id}`).
    suspend fun deleteTeam(teamId: String)
}
