package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.AgeGroupPlayers
import com.ricardofrango.squadify.data.database.Players
import com.ricardofrango.squadify.data.database.UserPlayers
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.AgeGroupPlayer
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.model.UserPlayerRelationship
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid
import com.ricardofrango.squadify.util.toUuid

class ExposedPlayerRepository : PlayerRepository {

    override suspend fun getPlayersByClub(clubId: String): List<Player> =
        Players.selectAll().where { Players.clubId eq clubId.toUuid() }
            .map(::toPlayer)

    override suspend fun createPlayer(player: Player): Player =
        Players.insert {
            it[id] = player.id.toUuid()
            it[clubId] = player.clubId.toUuid()
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
            this[Players.id] = it.id.toUuid()
            this[Players.clubId] = it.clubId.toUuid()
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
        Players.selectAll().where { Players.id eq id.toUuid() }
            .map(::toPlayer)
            .singleOrNull()

    override suspend fun updatePlayer(player: Player): Boolean =
        Players.update({ Players.id eq player.id.toUuid() }) {
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
            .where { Players.id inList playerIds.map(String::toUuid) }
            .map(::toPlayer)

    override suspend fun addPlayersToAgeGroup(links: List<AgeGroupPlayer>): List<AgeGroupPlayer> =
        AgeGroupPlayers.batchInsert(links) {
            this[AgeGroupPlayers.id] = it.id.toUuid()
            this[AgeGroupPlayers.ageGroupId] = it.ageGroupId.toUuid()
            this[AgeGroupPlayers.playerId] = it.playerId.toUuid()
        }.map(::toAgeGroupPlayer)

    override suspend fun getPlayersByAgeGroup(ageGroupId: String): List<Player> =
        (Players innerJoin AgeGroupPlayers).selectAll()
            .where { AgeGroupPlayers.ageGroupId eq ageGroupId.toUuid() }
            .map(::toPlayer)

    override suspend fun updateActiveStatus(playerId: String, active: Boolean): Boolean =
        Players.update({ Players.id eq playerId.toUuid() }) {
            it[Players.active] = active
        } > 0

    override suspend fun removePlayerFromAgeGroup(ageGroupId: String, playerId: String): Boolean =
        AgeGroupPlayers.deleteWhere {
            (AgeGroupPlayers.ageGroupId eq ageGroupId.toUuid()) and (AgeGroupPlayers.playerId eq playerId.toUuid())
        } > 0

    override suspend fun createUserPlayer(
        userId: String,
        playerId: String,
        relationship: UserPlayerRelationship
    ) {
        UserPlayers.insert {
            it[id] = Uuid.random()
            it[UserPlayers.userId] = userId.toUuid()
            it[UserPlayers.playerId] = playerId.toUuid()
            it[UserPlayers.relationship] = relationship
        }
    }

    override suspend fun createUserPlayers(userPlayers: List<Triple<String, String, UserPlayerRelationship>>) {
        UserPlayers.batchInsert(userPlayers) { (userId, playerId, rel) ->
            this[UserPlayers.id] = Uuid.random()
            this[UserPlayers.userId] = userId.toUuid()
            this[UserPlayers.playerId] = playerId.toUuid()
            this[UserPlayers.relationship] = rel
        }
    }

    override suspend fun deleteUserPlayersByPlayer(playerId: String) {
        UserPlayers.deleteWhere { UserPlayers.playerId eq playerId.toUuid() }
    }

    override suspend fun countPlayersByClub(clubId: String): Long =
        Players.selectAll().where { Players.clubId eq clubId.toUuid() }.count()

    override suspend fun getPlayersByUser(userId: String): List<Player> =
        (Players innerJoin UserPlayers).selectAll()
            .where { UserPlayers.userId eq userId.toUuid() }
            .map(::toPlayer)

    override suspend fun getUserPlayerLinksByClub(clubId: String): List<Triple<String, Player, UserPlayerRelationship>> =
        (Players innerJoin UserPlayers).selectAll()
            .where { Players.clubId eq clubId.toUuid() }
            .map { Triple(it[UserPlayers.userId].toString(), toPlayer(it), it[UserPlayers.relationship]) }

    override suspend fun getAgeGroupPlayersByClub(clubId: String): List<AgeGroupPlayer> =
        (AgeGroupPlayers innerJoin Players).selectAll()
            .where { Players.clubId eq clubId.toUuid() }
            .map(::toAgeGroupPlayer)

    override suspend fun countGuardiansByPlayer(playerId: String): Long =
        UserPlayers.selectAll().where { UserPlayers.playerId eq playerId.toUuid() }.count()

    override suspend fun getAgeGroupIdsByPlayer(playerId: String): List<String> =
        AgeGroupPlayers.selectAll().where { AgeGroupPlayers.playerId eq playerId.toUuid() }
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
