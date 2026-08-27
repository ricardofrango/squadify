package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamStaff
import com.ricardofrango.footballmanager.model.TeamStaffMember
import com.ricardofrango.footballmanager.model.TeamStaffRole

interface TeamRepository {
    suspend fun getTeamsByClub(clubId: String): List<Team>
    suspend fun getTeamById(id: String): Team?
    suspend fun createTeam(team: Team): Team
    suspend fun isUserStaffOfTeam(userId: String, teamId: String, role: TeamStaffRole? = null): Boolean
    suspend fun addStaffToTeam(teamStaff: TeamStaff): Boolean
    suspend fun getTeamStaff(teamId: String): List<TeamStaffMember>
}
