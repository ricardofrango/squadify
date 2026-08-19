package com.ricardofrango.footballmanager.data.repository

import com.ricardofrango.footballmanager.data.database.DatabaseFactory.dbQuery
import com.ricardofrango.footballmanager.data.database.Seasons
import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.exception.DatabaseException
import com.ricardofrango.footballmanager.model.Season
import com.ricardofrango.footballmanager.model.SeasonType
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

class ExposedSeasonRepository : SeasonRepository {

    override suspend fun getSeasonsByClub(clubId: String): List<Season> = dbQuery {
        Seasons.selectAll().where { Seasons.clubId eq Uuid.parse(clubId) }
            .map(::toSeason)
    }

    override suspend fun createSeason(season: Season): Season = dbQuery {
        Seasons.insert {
            it[id] = Uuid.parse(season.id)
            it[clubId] = Uuid.parse(season.clubId)
            it[name] = season.name
            it[type] = season.type
            it[startDate] = season.startDate
            it[endDate] = season.endDate
            it[active] = season.active
        }.resultedValues?.singleOrNull()?.let(::toSeason)
            ?: throw DatabaseException("Failed to create season")
    }

    override suspend fun getSeasonById(id: String): Season? = dbQuery {
        Seasons.selectAll().where { Seasons.id eq Uuid.parse(id) }
            .map(::toSeason)
            .singleOrNull()
    }

    override suspend fun findByDetails(
        clubId: String,
        type: SeasonType,
        startDate: LocalDate,
        endDate: LocalDate
    ): Season? = dbQuery {
        Seasons.selectAll().where {
            (Seasons.clubId eq Uuid.parse(clubId)) and
                    (Seasons.type eq type) and
                    (Seasons.startDate eq startDate) and
                    (Seasons.endDate eq endDate)
        }.map(::toSeason).singleOrNull()
    }

    private fun toSeason(row: ResultRow): Season = Season(
        id = row[Seasons.id].toString(),
        clubId = row[Seasons.clubId].toString(),
        name = row[Seasons.name],
        type = row[Seasons.type],
        startDate = row[Seasons.startDate],
        endDate = row[Seasons.endDate],
        active = row[Seasons.active]
    )
}
