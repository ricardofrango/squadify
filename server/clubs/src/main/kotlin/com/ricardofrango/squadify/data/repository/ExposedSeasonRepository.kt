package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.Seasons
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.exception.DatabaseException
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.SeasonType
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid
import com.ricardofrango.squadify.util.toUuid

class ExposedSeasonRepository : SeasonRepository {

    override suspend fun getSeasonsByClub(clubId: String): List<Season> =
        Seasons.selectAll().where { Seasons.clubId eq clubId.toUuid() }
            .map(::toSeason)

    override suspend fun createSeason(season: Season): Season =
        Seasons.insert {
            it[id] = season.id.toUuid()
            it[clubId] = season.clubId.toUuid()
            it[name] = season.name
            it[type] = season.type
            it[startDate] = season.startDate
            it[endDate] = season.endDate
            it[active] = season.active
        }.resultedValues?.singleOrNull()?.let(::toSeason)
            ?: throw DatabaseException("Failed to create season")

    override suspend fun getSeasonById(id: String): Season? =
        Seasons.selectAll().where { Seasons.id eq id.toUuid() }
            .map(::toSeason)
            .singleOrNull()

    override suspend fun updateSeason(season: Season): Boolean =
        Seasons.update({ Seasons.id eq season.id.toUuid() }) {
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
            (Seasons.clubId eq clubId.toUuid()) and
                    (Seasons.type eq type) and
                    (Seasons.startDate eq startDate) and
                    (Seasons.endDate eq endDate)
        }.map(::toSeason).singleOrNull()

    override suspend fun updateActiveStatus(seasonId: String, active: Boolean): Boolean =
        Seasons.update({ Seasons.id eq seasonId.toUuid() }) {
            it[Seasons.active] = active
        } > 0

    override suspend fun getActiveSeasonsByClub(clubId: String): List<Season> =
        Seasons.selectAll()
            .where { (Seasons.clubId eq clubId.toUuid()) and (Seasons.active eq true) }
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
