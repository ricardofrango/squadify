package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.data.database.Seasons
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.model.Season
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

class ExposedSeasonRepository : SeasonRepository {

    override suspend fun getSeasonsByClub(clubId: String): List<Season> = dbQuery {
        Seasons.selectAll().where { Seasons.clubId eq Uuid.parse(clubId) }
            .map(::toSeason)
    }

    override suspend fun createSeason(season: Season): Boolean = dbQuery {
        val insertStatement = Seasons.insert {
            it[id] = Uuid.parse(season.id)
            it[clubId] = Uuid.parse(season.clubId)
            it[name] = season.name
            it[startDate] = season.startDate
            it[endDate] = season.endDate
            it[active] = season.active
        }
        insertStatement.insertedCount > 0
    }

    override suspend fun getSeasonById(id: String): Season? = dbQuery {
        Seasons.selectAll().where { Seasons.id eq Uuid.parse(id) }
            .map(::toSeason)
            .singleOrNull()
    }

    private fun toSeason(row: ResultRow): Season = Season(
        id = row[Seasons.id].toString(),
        clubId = row[Seasons.clubId].toString(),
        name = row[Seasons.name],
        startDate = row[Seasons.startDate],
        endDate = row[Seasons.endDate],
        active = row[Seasons.active]
    )
}
