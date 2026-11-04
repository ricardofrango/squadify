package com.ricardofrango.squadify.data.repository

import com.ricardofrango.squadify.data.database.MatchPlayerStatsTable
import com.ricardofrango.squadify.domain.repository.MatchPlayerStatsRepository
import com.ricardofrango.squadify.model.MatchPlayerStats
import com.ricardofrango.squadify.util.toUuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ExposedMatchPlayerStatsRepository : MatchPlayerStatsRepository {

    override suspend fun replaceForMatch(matchId: String, stats: List<MatchPlayerStats>) {
        val matchUuid = matchId.toUuid()
        MatchPlayerStatsTable.deleteWhere { MatchPlayerStatsTable.matchId eq matchUuid }
        MatchPlayerStatsTable.batchInsert(stats) { s ->
            this[MatchPlayerStatsTable.id] = Uuid.random()
            this[MatchPlayerStatsTable.matchId] = matchUuid
            this[MatchPlayerStatsTable.playerId] = s.playerId.toUuid()
            this[MatchPlayerStatsTable.started] = s.started
            this[MatchPlayerStatsTable.minutesPlayed] = s.minutesPlayed
            this[MatchPlayerStatsTable.goals] = s.goals
            this[MatchPlayerStatsTable.assists] = s.assists
            this[MatchPlayerStatsTable.ownGoals] = s.ownGoals
            this[MatchPlayerStatsTable.yellowCards] = s.yellowCards
            this[MatchPlayerStatsTable.redCards] = s.redCards
            this[MatchPlayerStatsTable.rating] = s.rating
            this[MatchPlayerStatsTable.shots] = s.shots
            this[MatchPlayerStatsTable.shotsOnGoal] = s.shotsOnGoal
            this[MatchPlayerStatsTable.fouls] = s.fouls
        }
    }

    override suspend fun getByMatch(matchId: String): List<MatchPlayerStats> =
        MatchPlayerStatsTable.selectAll()
            .where { MatchPlayerStatsTable.matchId eq matchId.toUuid() }
            .map(::toStats)

    override suspend fun getByPlayer(playerId: String): List<MatchPlayerStats> =
        MatchPlayerStatsTable.selectAll()
            .where { MatchPlayerStatsTable.playerId eq playerId.toUuid() }
            .map(::toStats)

    override suspend fun getByMatches(matchIds: List<String>): List<MatchPlayerStats> {
        if (matchIds.isEmpty()) return emptyList()
        return MatchPlayerStatsTable.selectAll()
            .where { MatchPlayerStatsTable.matchId inList matchIds.map { it.toUuid() } }
            .map(::toStats)
    }

    // One UPDATE per rated player -- bounded by the squad size of a single match (a few dozen at most).
    override suspend fun updateRatings(matchId: String, ratings: Map<String, Int?>) {
        val matchUuid = matchId.toUuid()
        ratings.forEach { (playerId, rating) ->
            MatchPlayerStatsTable.update({
                (MatchPlayerStatsTable.matchId eq matchUuid) and (MatchPlayerStatsTable.playerId eq playerId.toUuid())
            }) {
                it[MatchPlayerStatsTable.rating] = rating
            }
        }
    }

    private fun toStats(row: ResultRow) = MatchPlayerStats(
        matchId = row[MatchPlayerStatsTable.matchId].toString(),
        playerId = row[MatchPlayerStatsTable.playerId].toString(),
        started = row[MatchPlayerStatsTable.started],
        minutesPlayed = row[MatchPlayerStatsTable.minutesPlayed],
        goals = row[MatchPlayerStatsTable.goals],
        assists = row[MatchPlayerStatsTable.assists],
        ownGoals = row[MatchPlayerStatsTable.ownGoals],
        yellowCards = row[MatchPlayerStatsTable.yellowCards],
        redCards = row[MatchPlayerStatsTable.redCards],
        rating = row[MatchPlayerStatsTable.rating],
        shots = row[MatchPlayerStatsTable.shots],
        shotsOnGoal = row[MatchPlayerStatsTable.shotsOnGoal],
        fouls = row[MatchPlayerStatsTable.fouls]
    )
}
