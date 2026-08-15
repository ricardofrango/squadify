package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Team

interface TeamRepository {
    suspend fun getTeamsByClub(clubId: String): List<Team>
    suspend fun createTeam(team: Team): Boolean
    suspend fun isUserStaffOfTeam(userId: String, teamId: String, role: String? = null): Boolean
}
