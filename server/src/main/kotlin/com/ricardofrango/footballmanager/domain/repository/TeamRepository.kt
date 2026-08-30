package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.TeamStaff
import com.ricardofrango.footballmanager.model.TeamStaffMember

interface TeamRepository {
    suspend fun getTeamsByClub(clubId: String): List<Team>
    suspend fun getTeamById(id: String): Team?
    suspend fun createTeam(team: Team): Team
    suspend fun getUserRoleInTeam(userId: String, teamId: String): TeamRole?
    suspend fun addStaffToTeam(teamStaff: TeamStaff): Boolean
    suspend fun getTeamStaff(teamId: String): List<TeamStaffMember>
    suspend fun updateActiveStatus(teamId: String, active: Boolean): Boolean
}
