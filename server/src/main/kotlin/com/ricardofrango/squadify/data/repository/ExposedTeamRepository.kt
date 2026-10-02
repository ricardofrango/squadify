package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.Team
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid
import com.ricardofrango.squadify.data.database.Teams as TeamsTable
import com.ricardofrango.squadify.util.toUuid

class ExposedTeamRepository : TeamRepository {

    override suspend fun getTeamsByClub(clubId: String): List<Team> =
        TeamsTable.selectAll()
            .where { TeamsTable.clubId eq clubId.toUuid() }
            .map(::toTeam)

    override suspend fun getTeamById(id: String): Team? =
        TeamsTable.selectAll()
            .where { TeamsTable.id eq id.toUuid() }
            .map(::toTeam)
            .singleOrNull()

    override suspend fun createTeam(team: Team): Team =
        TeamsTable.insert {
            it[id] = team.id.toUuid()
            it[clubId] = team.clubId.toUuid()
            it[ageGroupId] = team.ageGroupId.toUuid()
            it[seasonId] = team.seasonId.toUuid()
            it[name] = team.name
            it[code] = team.code
            it[active] = team.active
            it[teamSize] = team.teamSize
        }.resultedValues
            ?.singleOrNull()
            ?.let(::toTeam)
            ?: throw DatabaseException("Failed to create team")

    override suspend fun createTeams(teams: List<Team>): List<Team> =
        TeamsTable.batchInsert(teams) { team ->
            this[TeamsTable.id] = team.id.toUuid()
            this[TeamsTable.clubId] = team.clubId.toUuid()
            this[TeamsTable.ageGroupId] = team.ageGroupId.toUuid()
            this[TeamsTable.seasonId] = team.seasonId.toUuid()
            this[TeamsTable.name] = team.name
            this[TeamsTable.code] = team.code
            this[TeamsTable.active] = team.active
            this[TeamsTable.teamSize] = team.teamSize
        }.map(::toTeam)

    override suspend fun updateTeam(team: Team): Boolean =
        TeamsTable.update({ TeamsTable.id eq team.id.toUuid() }) {
            it[name] = team.name
            it[code] = team.code
            it[active] = team.active
            it[teamSize] = team.teamSize
        } > 0

    override suspend fun updateActiveStatus(teamId: String, active: Boolean): Boolean =
        TeamsTable.update({ TeamsTable.id eq teamId.toUuid() }) {
            it[TeamsTable.active] = active
        } > 0

    override suspend fun countTeamsByClub(clubId: String): Long =
        TeamsTable.selectAll()
            .where { TeamsTable.clubId eq clubId.toUuid() }
            .count()

    private fun toTeam(row: ResultRow): Team = Team(
        id = row[TeamsTable.id].toString(),
        clubId = row[TeamsTable.clubId].toString(),
        ageGroupId = row[TeamsTable.ageGroupId].toString(),
        seasonId = row[TeamsTable.seasonId].toString(),
        name = row[TeamsTable.name],
        code = row[TeamsTable.code],
        active = row[TeamsTable.active],
        teamSize = row[TeamsTable.teamSize]
    )
}
