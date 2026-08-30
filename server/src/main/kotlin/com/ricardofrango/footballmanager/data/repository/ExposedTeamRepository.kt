package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.data.database.TeamStaff as TeamStaffTable
import com.ricardofrango.footballmanager.data.database.Teams as TeamsTable
import com.ricardofrango.footballmanager.data.database.Users as UsersTable
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.Team
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.TeamStaff
import com.ricardofrango.footballmanager.model.TeamStaffMember
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ExposedTeamRepository : TeamRepository {

    override suspend fun getTeamsByClub(clubId: String): List<Team> = dbQuery {
        TeamsTable.selectAll().where { TeamsTable.clubId eq Uuid.parse(clubId) }
            .map(::toTeam)
    }

    override suspend fun getTeamById(id: String): Team? = dbQuery {
        TeamsTable.selectAll().where { TeamsTable.id eq Uuid.parse(id) }
            .map(::toTeam)
            .singleOrNull()
    }

    override suspend fun createTeam(team: Team): Team = dbQuery {
        TeamsTable.insert {
            it[id] = Uuid.parse(team.id)
            it[clubId] = Uuid.parse(team.clubId)
            it[ageGroupId] = Uuid.parse(team.ageGroupId)
            it[seasonId] = Uuid.parse(team.seasonId)
            it[name] = team.name
            it[code] = team.code
            it[active] = team.active
        }.resultedValues?.singleOrNull()?.let(::toTeam)
            ?: throw DatabaseException("Failed to create team")
    }

    override suspend fun updateTeam(team: Team): Boolean = dbQuery {
        TeamsTable.update({ TeamsTable.id eq Uuid.parse(team.id) }) {
            it[name] = team.name
            it[code] = team.code
            it[active] = team.active
        } > 0
    }

    override suspend fun getUserRoleInTeam(userId: String, teamId: String): TeamRole? = dbQuery {
        TeamStaffTable.selectAll().where {
            (TeamStaffTable.userId eq Uuid.parse(userId)) and (TeamStaffTable.teamId eq Uuid.parse(teamId))
        }.map { it[TeamStaffTable.role] }
            .singleOrNull()
    }

    override suspend fun addStaffToTeam(teamStaff: TeamStaff): Boolean = dbQuery {
        val insertStatement = TeamStaffTable.insert {
            it[id] = Uuid.parse(teamStaff.id)
            it[teamId] = Uuid.parse(teamStaff.teamId)
            it[userId] = Uuid.parse(teamStaff.userId)
            it[role] = teamStaff.role
        }
        insertStatement.insertedCount > 0
    }

    override suspend fun getTeamStaff(teamId: String): List<TeamStaffMember> = dbQuery {
        (TeamStaffTable innerJoin UsersTable).selectAll()
            .where { TeamStaffTable.teamId eq Uuid.parse(teamId) }
            .map {
                TeamStaffMember(
                    userId = it[UsersTable.id].toString(),
                    firstName = it[UsersTable.firstName],
                    lastName = it[UsersTable.lastName],
                    role = it[TeamStaffTable.role],
                    avatarUrl = it[UsersTable.avatarUrl]
                )
            }
    }

    override suspend fun updateActiveStatus(teamId: String, active: Boolean): Boolean = dbQuery {
        TeamsTable.update({ TeamsTable.id eq Uuid.parse(teamId) }) {
            it[TeamsTable.active] = active
        } > 0
    }

    private fun toTeam(row: ResultRow): Team = Team(
        id = row[TeamsTable.id].toString(),
        clubId = row[TeamsTable.clubId].toString(),
        ageGroupId = row[TeamsTable.ageGroupId].toString(),
        seasonId = row[TeamsTable.seasonId].toString(),
        name = row[TeamsTable.name],
        code = row[TeamsTable.code],
        active = row[TeamsTable.active]
    )
}
