package com.ricardofrango.clubmanager.data.repository

import com.ricardofrango.clubmanager.data.database.Seasons
import com.ricardofrango.clubmanager.domain.repository.SeasonRepository
import com.ricardofrango.clubmanager.exception.DatabaseException
import com.ricardofrango.clubmanager.model.Season
import com.ricardofrango.clubmanager.model.SeasonType
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ExposedSeasonRepository : SeasonRepository {

    override suspend fun getSeasonsByClub(clubId: String): List<Season> =
        Seasons.selectAll().where { Seasons.clubId eq Uuid.parse(clubId) }
            .map(::toSeason)

    override suspend fun createSeason(season: Season): Season =
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

    override suspend fun getSeasonById(id: String): Season? =
        Seasons.selectAll().where { Seasons.id eq Uuid.parse(id) }
            .map(::toSeason)
            .singleOrNull()

    override suspend fun updateSeason(season: Season): Boolean =
        Seasons.update({ Seasons.id eq Uuid.parse(season.id) }) {
            it[name] = season.name
            it[type] = season.type
            it[startDate] = season.startDate
            it[endDate] = season.endDate
            it[active] = season.active
        } > 0

    override suspend fun findByDetails(
        clubId: String,
        type: SeasonType,
        startDate: LocalDate,
        endDate: LocalDate
    ): Season? =
        Seasons.selectAll().where {
            (Seasons.clubId eq Uuid.parse(clubId)) and
                    (Seasons.type eq type) and
                    (Seasons.startDate eq startDate) and
                    (Seasons.endDate eq endDate)
        }.map(::toSeason).singleOrNull()

    override suspend fun updateActiveStatus(seasonId: String, active: Boolean): Boolean =
        Seasons.update({ Seasons.id eq Uuid.parse(seasonId) }) {
            it[Seasons.active] = active
        } > 0

    override suspend fun getActiveSeasonsByClub(clubId: String): List<Season> =
        Seasons.selectAll()
            .where { (Seasons.clubId eq Uuid.parse(clubId)) and (Seasons.active eq true) }
            .map(::toSeason)

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
