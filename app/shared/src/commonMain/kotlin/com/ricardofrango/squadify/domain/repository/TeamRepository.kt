package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.CreateTeamsRequest
import com.ricardofrango.squadify.model.Team
import com.ricardofrango.squadify.model.TeamDetails

interface TeamRepository {
    suspend fun getTeams(clubId: String): List<Team>
    suspend fun getTeamDetails(teamId: String): TeamDetails
    suspend fun createTeams(request: CreateTeamsRequest): List<Team>
}
