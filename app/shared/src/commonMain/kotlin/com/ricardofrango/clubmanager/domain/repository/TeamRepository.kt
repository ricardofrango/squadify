package com.ricardofrango.clubmanager.domain.repository

import com.ricardofrango.clubmanager.model.CreateTeamsRequest
import com.ricardofrango.clubmanager.model.Team
import com.ricardofrango.clubmanager.model.TeamDetails

interface TeamRepository {
    suspend fun getTeams(clubId: String): List<Team>
    suspend fun getTeamDetails(teamId: String): TeamDetails
    suspend fun createTeams(request: CreateTeamsRequest): List<Team>
}
