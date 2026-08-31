package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.Opponent
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid
import com.ricardofrango.footballmanager.data.database.Opponents as OpponentsTable

class ExposedOpponentRepository : OpponentRepository {

    override suspend fun getOpponentById(id: String): Opponent? =
        OpponentsTable.selectAll().where { OpponentsTable.id eq Uuid.parse(id) }
            .map(::toOpponent)
            .singleOrNull()

    override suspend fun getOpponentsByClub(clubId: String): List<Opponent> =
        OpponentsTable.selectAll().where { OpponentsTable.clubId eq Uuid.parse(clubId) }
            .map(::toOpponent)

    override suspend fun createOpponent(opponent: Opponent): Opponent =
        OpponentsTable.insert {
            it[id] = Uuid.parse(opponent.id)
            it[clubId] = Uuid.parse(opponent.clubId)
            it[name] = opponent.name
            it[shortName] = opponent.shortName
            it[logoUrl] = opponent.logoUrl
        }.resultedValues
            ?.singleOrNull()
            ?.let(::toOpponent)
            ?: throw DatabaseException("Failed to create opponent")

    override suspend fun updateOpponent(opponent: Opponent): Boolean =
        OpponentsTable.update({ OpponentsTable.id eq Uuid.parse(opponent.id) }) {
            it[name] = opponent.name
            it[shortName] = opponent.shortName
            it[logoUrl] = opponent.logoUrl
        } > 0

    override suspend fun deleteOpponent(id: String): Boolean =
        OpponentsTable.deleteWhere { OpponentsTable.id eq Uuid.parse(id) } > 0

    private fun toOpponent(row: ResultRow): Opponent = Opponent(
        id = row[OpponentsTable.id].toString(),
        clubId = row[OpponentsTable.clubId].toString(),
        name = row[OpponentsTable.name],
        shortName = row[OpponentsTable.shortName],
        logoUrl = row[OpponentsTable.logoUrl]
    )
}
