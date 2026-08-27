package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.data.database.Players
import com.ricardofrango.footballmanager.data.database.Players.active
import com.ricardofrango.footballmanager.data.database.Players.birthDate
import com.ricardofrango.footballmanager.data.database.Players.clubId
import com.ricardofrango.footballmanager.data.database.Players.firstName
import com.ricardofrango.footballmanager.data.database.Players.id
import com.ricardofrango.footballmanager.data.database.Players.lastName
import com.ricardofrango.footballmanager.data.database.Players.photoUrl
import com.ricardofrango.footballmanager.data.database.Players.shirtNumber
import com.ricardofrango.footballmanager.data.database.TeamPlayers
import com.ricardofrango.footballmanager.data.database.TeamPlayers.endDate
import com.ricardofrango.footballmanager.data.database.TeamPlayers.playerId
import com.ricardofrango.footballmanager.data.database.TeamPlayers.startDate
import com.ricardofrango.footballmanager.data.database.TeamPlayers.status
import com.ricardofrango.footballmanager.data.database.TeamPlayers.teamId
import com.ricardofrango.footballmanager.domain.repository.PlayerRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.Player
import com.ricardofrango.footballmanager.model.TeamPlayer
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
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

    override suspend fun createPlayers(players: List<Player>): List<Player> = dbQuery {
        Players.batchInsert(players) {
            this[id] = Uuid.parse(it.id)
            this[clubId] = Uuid.parse(it.clubId)
            this[firstName] = it.firstName
            this[lastName] = it.lastName
            this[birthDate] = it.birthDate
            this[shirtNumber] = it.shirtNumber
            this[photoUrl] = it.photoUrl
            this[active] = it.active
        }.map(::toPlayer)
    }

    override suspend fun getPlayerById(id: String): Player? = dbQuery {
        Players.selectAll().where { Players.id eq Uuid.parse(id) }
            .map(::toPlayer)
            .singleOrNull()
    }

    override suspend fun getPlayersData(playerIds: List<String>): List<Player> = dbQuery {
        Players.selectAll()
            .where { Players.id inList playerIds.map(Uuid::parse) }
            .map(::toPlayer)
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

    override suspend fun addPlayersToTeam(teamPlayer: List<TeamPlayer>): List<TeamPlayer> = dbQuery {
        TeamPlayers.batchInsert(teamPlayer) {
            this[TeamPlayers.id] = Uuid.parse(it.id)
            this[teamId] = Uuid.parse(it.teamId)
            this[playerId] = Uuid.parse(it.playerId)
            this[startDate] = it.startDate
            this[endDate] = it.endDate
            this[status] = it.status
        }.map(::toTeamPlayer)
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
