package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.data.database.Players
import com.ricardofrango.footballmanager.data.database.TeamPlayers
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.TeamPlayer
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

class ExposedPlayerRepository : PlayerRepository {

    override suspend fun getPlayersByClub(clubId: String): List<Player> = dbQuery {
        Players.selectAll().where { Players.clubId eq Uuid.parse(clubId) }
            .map(::toPlayer)
    }

    override suspend fun createPlayer(player: Player): Player = dbQuery {
        Players.insert {
            it[id] = Uuid.parse(player.id)
            it[clubId] = Uuid.parse(player.clubId)
            it[firstName] = player.firstName
            it[lastName] = player.lastName
            it[birthDate] = player.birthDate
            it[shirtNumber] = player.shirtNumber
            it[photoUrl] = player.photoUrl
            it[active] = player.active
        }.resultedValues?.singleOrNull()?.let(::toPlayer)
            ?: throw DatabaseException("Failed to create player")
    }

    override suspend fun getPlayerById(id: String): Player? = dbQuery {
        Players.selectAll().where { Players.id eq Uuid.parse(id) }
            .map(::toPlayer)
            .singleOrNull()
    }

    override suspend fun addPlayerToTeam(teamPlayer: TeamPlayer): TeamPlayer = dbQuery {
        TeamPlayers.insert {
            it[id] = Uuid.parse(teamPlayer.id)
            it[teamId] = Uuid.parse(teamPlayer.teamId)
            it[playerId] = Uuid.parse(teamPlayer.playerId)
            it[startDate] = teamPlayer.startDate
            it[endDate] = teamPlayer.endDate
            it[status] = teamPlayer.status
        }.resultedValues?.singleOrNull()?.let(::toTeamPlayer)
            ?: throw DatabaseException("Failed to add player to team")
    }

    override suspend fun getPlayersByTeam(teamId: String): List<Player> = dbQuery {
        (Players innerJoin TeamPlayers).selectAll()
            .where { TeamPlayers.teamId eq Uuid.parse(teamId) }
            .map(::toPlayer)
    }

    private fun toTeamPlayer(row: ResultRow): TeamPlayer = TeamPlayer(
        id = row[TeamPlayers.id].toString(),
        teamId = row[TeamPlayers.teamId].toString(),
        playerId = row[TeamPlayers.playerId].toString(),
        startDate = row[TeamPlayers.startDate],
        endDate = row[TeamPlayers.endDate],
        status = row[TeamPlayers.status]
    )

    private fun toPlayer(row: ResultRow): Player = Player(
        id = row[Players.id].toString(),
        clubId = row[Players.clubId].toString(),
        firstName = row[Players.firstName],
        lastName = row[Players.lastName],
        birthDate = row[Players.birthDate],
        shirtNumber = row[Players.shirtNumber],
        photoUrl = row[Players.photoUrl],
        active = row[Players.active]
    )
}
