package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.Opponent
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid
import com.ricardofrango.squadify.data.database.Opponents as OpponentsTable
import com.ricardofrango.squadify.util.toUuid

class ExposedOpponentRepository : OpponentRepository {

    override suspend fun getOpponentById(id: String): Opponent? =
        OpponentsTable.selectAll().where { OpponentsTable.id eq id.toUuid() }
            .map(::toOpponent)
            .singleOrNull()

    override suspend fun getOpponentsByClub(clubId: String): List<Opponent> =
        OpponentsTable.selectAll().where { OpponentsTable.clubId eq clubId.toUuid() }
            .map(::toOpponent)

    override suspend fun createOpponent(opponent: Opponent): Opponent =
        OpponentsTable.insert {
            it[id] = opponent.id.toUuid()
            it[clubId] = opponent.clubId.toUuid()
            it[name] = opponent.name
            it[shortName] = opponent.shortName
            it[logoUrl] = opponent.logoUrl
            it[stadiumName] = opponent.stadiumName
            it[stadiumAddress] = opponent.stadiumAddress
            it[contactEmail] = opponent.contactEmail
            it[contactPhone] = opponent.contactPhone
            it[notes] = opponent.notes
        }.resultedValues
            ?.singleOrNull()
            ?.let(::toOpponent)
            ?: throw DatabaseException("Failed to create opponent")

    override suspend fun updateOpponent(opponent: Opponent): Boolean =
        OpponentsTable.update({ OpponentsTable.id eq opponent.id.toUuid() }) {
            it[name] = opponent.name
            it[shortName] = opponent.shortName
            it[logoUrl] = opponent.logoUrl
            it[stadiumName] = opponent.stadiumName
            it[stadiumAddress] = opponent.stadiumAddress
            it[contactEmail] = opponent.contactEmail
            it[contactPhone] = opponent.contactPhone
            it[notes] = opponent.notes
        } > 0

    override suspend fun deleteOpponent(id: String): Boolean =
        OpponentsTable.deleteWhere { OpponentsTable.id eq id.toUuid() } > 0

    private fun toOpponent(row: ResultRow): Opponent = Opponent(
        id = row[OpponentsTable.id].toString(),
        clubId = row[OpponentsTable.clubId].toString(),
        name = row[OpponentsTable.name],
        shortName = row[OpponentsTable.shortName],
        logoUrl = row[OpponentsTable.logoUrl],
        stadiumName = row[OpponentsTable.stadiumName],
        stadiumAddress = row[OpponentsTable.stadiumAddress],
        contactEmail = row[OpponentsTable.contactEmail],
        contactPhone = row[OpponentsTable.contactPhone],
        notes = row[OpponentsTable.notes]
    )
}
