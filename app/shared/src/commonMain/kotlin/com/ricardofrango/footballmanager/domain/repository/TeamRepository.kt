package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamDetails

interface TeamRepository {
    suspend fun getTeams(clubId: String): List<Team>
    suspend fun getTeamDetails(teamId: String): TeamDetails
}
