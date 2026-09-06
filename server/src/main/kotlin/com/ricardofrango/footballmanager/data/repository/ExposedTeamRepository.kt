package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.Team
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid
import com.ricardofrango.footballmanager.data.database.Teams as TeamsTable

class ExposedTeamRepository : TeamRepository {

    override suspend fun getTeamsByClub(clubId: String): List<Team> =
        TeamsTable.selectAll()
            .where { TeamsTable.clubId eq Uuid.parse(clubId) }
            .map(::toTeam)

    override suspend fun getTeamById(id: String): Team? =
        TeamsTable.selectAll()
            .where { TeamsTable.id eq Uuid.parse(id) }
            .map(::toTeam)
            .singleOrNull()

    override suspend fun createTeam(team: Team): Team =
        TeamsTable.insert {
            it[id] = Uuid.parse(team.id)
            it[clubId] = Uuid.parse(team.clubId)
            it[ageGroupId] = Uuid.parse(team.ageGroupId)
            it[seasonId] = Uuid.parse(team.seasonId)
            it[name] = team.name
            it[code] = team.code
            it[active] = team.active
        }.resultedValues
            ?.singleOrNull()
            ?.let(::toTeam)
            ?: throw DatabaseException("Failed to create team")

    override suspend fun createTeams(teams: List<Team>): List<Team> =
        TeamsTable.batchInsert(teams) { team ->
            this[TeamsTable.id] = Uuid.parse(team.id)
            this[TeamsTable.clubId] = Uuid.parse(team.clubId)
            this[TeamsTable.ageGroupId] = Uuid.parse(team.ageGroupId)
            this[TeamsTable.seasonId] = Uuid.parse(team.seasonId)
            this[TeamsTable.name] = team.name
            this[TeamsTable.code] = team.code
            this[TeamsTable.active] = team.active
        }.map(::toTeam)

    override suspend fun updateTeam(team: Team): Boolean =
        TeamsTable.update({ TeamsTable.id eq Uuid.parse(team.id) }) {
            it[name] = team.name
            it[code] = team.code
            it[active] = team.active
        } > 0

    override suspend fun updateActiveStatus(teamId: String, active: Boolean): Boolean =
        TeamsTable.update({ TeamsTable.id eq Uuid.parse(teamId) }) {
            it[TeamsTable.active] = active
        } > 0

    override suspend fun countTeamsByClub(clubId: String): Long =
        TeamsTable.selectAll()
            .where { TeamsTable.clubId eq Uuid.parse(clubId) }
            .count()

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
