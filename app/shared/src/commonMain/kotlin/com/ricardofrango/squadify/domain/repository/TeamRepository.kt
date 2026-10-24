package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.CreateTeamsRequest
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamDetails
import com.ricardofrango.squadify.model.TeamSeasonRecord

interface TeamRepository {
    suspend fun getTeams(clubId: String): List<Team>
    suspend fun getTeamDetails(teamId: String): TeamDetails
    suspend fun getTeamSeasonRecord(teamId: String): TeamSeasonRecord
    suspend fun getTeamFullStats(teamId: String): com.ricardofrango.squadify.model.FullStatsData
    suspend fun createTeams(request: CreateTeamsRequest): List<Team>
    suspend fun updateTeam(teamId: String, request: com.ricardofrango.squadify.model.UpdateTeamRequest)
    // Deletes the team and all its matches (`DELETE /teams/{id}`).
    suspend fun deleteTeam(teamId: String)
}
