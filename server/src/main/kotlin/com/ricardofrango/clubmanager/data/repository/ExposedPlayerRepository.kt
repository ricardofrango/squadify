package com.ricardofrango.clubmanager.data.repository

import com.ricardofrango.clubmanager.data.database.AgeGroupPlayers
import com.ricardofrango.clubmanager.data.database.Players
import com.ricardofrango.clubmanager.data.database.UserPlayers
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.exception.DatabaseException
import com.ricardofrango.clubmanager.model.AgeGroupPlayer
import com.ricardofrango.clubmanager.model.Player
import com.ricardofrango.clubmanager.model.UserPlayerRelationship
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ExposedPlayerRepository : PlayerRepository {

    override suspend fun getPlayersByClub(clubId: String): List<Player> =
        Players.selectAll().where { Players.clubId eq Uuid.parse(clubId) }
            .map(::toPlayer)

    override suspend fun createPlayer(player: Player): Player =
        Players.insert {
            it[id] = Uuid.parse(player.id)
            it[clubId] = Uuid.parse(player.clubId)
            it[firstName] = player.firstName
            it[lastName] = player.lastName
            it[birthDate] = player.birthDate
            it[shirtNumber] = player.shirtNumber
            it[photoUrl] = player.photoUrl
            it[active] = player.active
            it[position] = player.position
            it[heightCm] = player.heightCm
            it[weightKg] = player.weightKg
            it[emergencyContact] = player.emergencyContact
        }.resultedValues?.singleOrNull()?.let(::toPlayer)
            ?: throw DatabaseException("Failed to create player")

    override suspend fun createPlayers(players: List<Player>): List<Player> =
        Players.batchInsert(players) {
            this[Players.id] = Uuid.parse(it.id)
            this[Players.clubId] = Uuid.parse(it.clubId)
            this[Players.firstName] = it.firstName
            this[Players.lastName] = it.lastName
            this[Players.birthDate] = it.birthDate
            this[Players.shirtNumber] = it.shirtNumber
            this[Players.photoUrl] = it.photoUrl
            this[Players.active] = it.active
            this[Players.position] = it.position
            this[Players.heightCm] = it.heightCm
            this[Players.weightKg] = it.weightKg
            this[Players.emergencyContact] = it.emergencyContact
        }.map(::toPlayer)

    override suspend fun getPlayerById(id: String): Player? =
        Players.selectAll().where { Players.id eq Uuid.parse(id) }
            .map(::toPlayer)
            .singleOrNull()

    override suspend fun updatePlayer(player: Player): Boolean =
        Players.update({ Players.id eq Uuid.parse(player.id) }) {
            it[firstName] = player.firstName
            it[lastName] = player.lastName
            it[birthDate] = player.birthDate
            it[shirtNumber] = player.shirtNumber
            it[photoUrl] = player.photoUrl
            it[active] = player.active
            it[position] = player.position
            it[heightCm] = player.heightCm
            it[weightKg] = player.weightKg
            it[emergencyContact] = player.emergencyContact
        } > 0

    override suspend fun getPlayersData(playerIds: List<String>): List<Player> =
        Players.selectAll()
            .where { Players.id inList playerIds.map(Uuid::parse) }
            .map(::toPlayer)

    override suspend fun addPlayersToAgeGroup(links: List<AgeGroupPlayer>): List<AgeGroupPlayer> =
        AgeGroupPlayers.batchInsert(links) {
            this[AgeGroupPlayers.id] = Uuid.parse(it.id)
            this[AgeGroupPlayers.ageGroupId] = Uuid.parse(it.ageGroupId)
            this[AgeGroupPlayers.playerId] = Uuid.parse(it.playerId)
        }.map(::toAgeGroupPlayer)

    override suspend fun getPlayersByAgeGroup(ageGroupId: String): List<Player> =
        (Players innerJoin AgeGroupPlayers).selectAll()
            .where { AgeGroupPlayers.ageGroupId eq Uuid.parse(ageGroupId) }
            .map(::toPlayer)

    override suspend fun updateActiveStatus(playerId: String, active: Boolean): Boolean =
        Players.update({ Players.id eq Uuid.parse(playerId) }) {
            it[Players.active] = active
        } > 0

    override suspend fun createUserPlayer(
        userId: String,
        playerId: String,
        relationship: UserPlayerRelationship
    ) {
        UserPlayers.insert {
            it[id] = Uuid.random()
            it[UserPlayers.userId] = Uuid.parse(userId)
            it[UserPlayers.playerId] = Uuid.parse(playerId)
            it[UserPlayers.relationship] = relationship
        }
    }

    override suspend fun createUserPlayers(userPlayers: List<Triple<String, String, UserPlayerRelationship>>) {
        UserPlayers.batchInsert(userPlayers) { (userId, playerId, rel) ->
            this[UserPlayers.id] = Uuid.random()
            this[UserPlayers.userId] = Uuid.parse(userId)
            this[UserPlayers.playerId] = Uuid.parse(playerId)
            this[UserPlayers.relationship] = rel
        }
    }

    override suspend fun deleteUserPlayersByPlayer(playerId: String) {
        UserPlayers.deleteWhere { UserPlayers.playerId eq Uuid.parse(playerId) }
    }

    override suspend fun countPlayersByClub(clubId: String): Long =
        Players.selectAll().where { Players.clubId eq Uuid.parse(clubId) }.count()

    override suspend fun getPlayersByUser(userId: String): List<Player> =
        (Players innerJoin UserPlayers).selectAll()
            .where { UserPlayers.userId eq Uuid.parse(userId) }
            .map(::toPlayer)

    override suspend fun getAgeGroupPlayersByClub(clubId: String): List<AgeGroupPlayer> =
        (AgeGroupPlayers innerJoin Players).selectAll()
            .where { Players.clubId eq Uuid.parse(clubId) }
            .map(::toAgeGroupPlayer)

    override suspend fun countGuardiansByPlayer(playerId: String): Long =
        UserPlayers.selectAll().where { UserPlayers.playerId eq Uuid.parse(playerId) }.count()

    override suspend fun getAgeGroupIdsByPlayer(playerId: String): List<String> =
        AgeGroupPlayers.selectAll().where { AgeGroupPlayers.playerId eq Uuid.parse(playerId) }
            .map { it[AgeGroupPlayers.ageGroupId].toString() }

    private fun toAgeGroupPlayer(row: ResultRow): AgeGroupPlayer = AgeGroupPlayer(
        id = row[AgeGroupPlayers.id].toString(),
        ageGroupId = row[AgeGroupPlayers.ageGroupId].toString(),
        playerId = row[AgeGroupPlayers.playerId].toString()
    )

    private fun toPlayer(row: ResultRow): Player = Player(
        id = row[Players.id].toString(),
        clubId = row[Players.clubId].toString(),
        firstName = row[Players.firstName],
        lastName = row[Players.lastName],
        birthDate = row[Players.birthDate],
        shirtNumber = row[Players.shirtNumber],
        photoUrl = row[Players.photoUrl],
        active = row[Players.active],
        position = row[Players.position],
        heightCm = row[Players.heightCm],
        weightKg = row[Players.weightKg],
        emergencyContact = row[Players.emergencyContact]
    )
}
