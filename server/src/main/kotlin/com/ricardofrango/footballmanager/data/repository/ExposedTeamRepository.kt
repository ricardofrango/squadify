package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.data.database.TeamStaff
import com.ricardofrango.footballmanager.data.database.Teams
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.model.Team
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.andWhere
import kotlin.uuid.Uuid

class ExposedTeamRepository : TeamRepository {

    override suspend fun getTeamsByClub(clubId: String): List<Team> = dbQuery {
        Teams.selectAll().where { Teams.clubId eq Uuid.parse(clubId) }
            .map(::toTeam)
    }

    override suspend fun createTeam(team: Team): Boolean = dbQuery {
        val insertStatement = Teams.insert {
            it[id] = Uuid.parse(team.id)
            it[clubId] = Uuid.parse(team.clubId)
            it[ageGroupId] = Uuid.parse(team.ageGroupId)
            it[seasonId] = Uuid.parse(team.seasonId)
            it[name] = team.name
            it[code] = team.code
            it[active] = team.active
        }
        insertStatement.insertedCount > 0
    }

    override suspend fun isUserStaffOfTeam(userId: String, teamId: String, role: String?): Boolean = dbQuery {
        val query = TeamStaff.selectAll().where { 
            (TeamStaff.userId eq Uuid.parse(userId)) and (TeamStaff.teamId eq Uuid.parse(teamId)) 
        }
        
        if (role != null) {
            query.andWhere { TeamStaff.role eq role }
        }
        
        !query.empty()
    }

    private fun toTeam(row: ResultRow): Team = Team(
        id = row[Teams.id].toString(),
        clubId = row[Teams.clubId].toString(),
        ageGroupId = row[Teams.ageGroupId].toString(),
        seasonId = row[Teams.seasonId].toString(),
        name = row[Teams.name],
        code = row[Teams.code],
        active = row[Teams.active]
    )
}
