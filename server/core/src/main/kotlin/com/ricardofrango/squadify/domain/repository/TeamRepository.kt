package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.Team

interface TeamRepository {
    suspend fun getTeamsByClub(clubId: String): List<Team>
    suspend fun getTeamById(id: String): Team?
    suspend fun createTeam(team: Team): Team
    suspend fun createTeams(teams: List<Team>): List<Team>
    suspend fun updateTeam(team: Team): Boolean
    suspend fun updateActiveStatus(teamId: String, active: Boolean): Boolean
    suspend fun countTeamsByClub(clubId: String): Long
    // Cascades (V21): its matches go too, with their call-ups, events, player stats and reports.
    suspend fun deleteTeam(teamId: String): Boolean
}
