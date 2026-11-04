package com.ricardofrango.squadify.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.time.Instant

// The Full stats screen (REQUIREMENTS: "Full stats screen"): every player's stats in one table, for a
// team or for an age group. As with SeasonStats.kt, the server sends the season's raw rows and every
// number is derived here, so the date range, match type, team filter and sort need no round trip.

/** A finished match of the team (or of one of the age group's teams), with its final score. */
@Serializable
data class FullStatsMatch(
    val matchId: String,
    val teamId: String,
    val dateTime: Instant,
    val matchType: MatchType? = null,
    val ourScore: Int,
    val opponentScore: Int
)

/** A called-up player's line in one of [FullStatsData.matches]. [rating] only for staff, from a completed report. */
@Serializable
data class FullStatsLine(
    val matchId: String,
    val playerId: String,
    val started: Boolean,
    val minutesPlayed: Int,
    val goals: Int,
    val assists: Int,
    val yellowCards: Int,
    val redCards: Int,
    val rating: Int? = null,
    // As in MatchPlayerStats: a goal is also a shot on goal, a shot on goal also a shot.
    val shots: Int = 0,
    val shotsOnGoal: Int = 0,
    val fouls: Int = 0
) {
    val played: Boolean get() = started || minutesPlayed > 0
}

/** An ended, not cancelled training session of the age group with its ratings (0 = did not take part). */
@Serializable
data class FullStatsTraining(
    val sessionId: String,
    val date: LocalDate,
    val ratings: List<TrainingRating>
)

@Serializable
data class FullStatsTeam(val id: String, val name: String)

/**
 * `GET /teams/{id}/full-stats` and `GET /age-groups/{id}/full-stats` return the same: the age group's
 * data ([title] is its name), with [teams] all of its teams and [players] its pool. Entering from a team
 * only preselects it in the team filter ([FullStatsQuery.teamId]). [trainings] are empty unless
 * [canSeeRatings] (training ratings are staff only).
 */
@Serializable
data class FullStatsData(
    val title: String,
    val ageGroupId: String,
    val seasonId: String,
    val seasonName: String,
    val seasonStart: LocalDate,
    val seasonEnd: LocalDate,
    val teams: List<FullStatsTeam>,
    val matches: List<FullStatsMatch>,
    val lines: List<FullStatsLine>,
    val trainings: List<FullStatsTraining>,
    val players: List<TeamSeasonPlayer>,
    val canSeeRatings: Boolean
)

/**
 * The table's stat columns, in order (the developer's, 2026-10-24: the rankings first; 2026-11-04: the
 * position before them, by the player). The three rankings are staff only.
 */
enum class FullStatsColumn(val rating: Boolean = false) {
    POSITION,
    FINAL_RATING(true), TRAINING_RATING(true), GAMES_RATING(true),
    WON, DRAWN, LOST,
    GOALS, ASSISTS, MINUTES, SHOTS, SHOTS_ON_GOAL, FOULS, YELLOW_CARDS, RED_CARDS
}

/** Which rows count: a date range (inclusive; null = open), the match type, and one of the teams (null = the whole age group). */
data class FullStatsQuery(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val type: StatsFilter = StatsFilter.ALL,
    val teamId: String? = null
)

data class FullStatsRow(
    val player: TeamSeasonPlayer,
    val games: Int,
    val goals: Int,
    val assists: Int,
    val minutes: Int,
    // Averages, one decimal; null with nothing rated.
    val gamesRating: Double?,
    val trainingRating: Double?,
    val finalRating: Double?,
    // How many ratings, games and trainings together, [finalRating] averages.
    val ratings: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val yellowCards: Int,
    val redCards: Int,
    val shots: Int = 0,
    val shotsOnGoal: Int = 0,
    val fouls: Int = 0
) {
    fun value(column: FullStatsColumn): Double? = when (column) {
        // Goalkeeper to striker (PlayerPosition's order); none: no value, so last.
        FullStatsColumn.POSITION -> player.position?.ordinal?.toDouble()
        FullStatsColumn.GOALS -> goals.toDouble()
        FullStatsColumn.ASSISTS -> assists.toDouble()
        FullStatsColumn.MINUTES -> minutes.toDouble()
        FullStatsColumn.GAMES_RATING -> gamesRating
        FullStatsColumn.TRAINING_RATING -> trainingRating
        FullStatsColumn.FINAL_RATING -> finalRating
        FullStatsColumn.WON -> won.toDouble()
        FullStatsColumn.DRAWN -> drawn.toDouble()
        FullStatsColumn.LOST -> lost.toDouble()
        FullStatsColumn.YELLOW_CARDS -> yellowCards.toDouble()
        FullStatsColumn.RED_CARDS -> redCards.toDouble()
        FullStatsColumn.SHOTS -> shots.toDouble()
        FullStatsColumn.SHOTS_ON_GOAL -> shotsOnGoal.toDouble()
        FullStatsColumn.FOULS -> fouls.toDouble()
    }
}

/** The rows, unsorted, and how many games and trainings fall in the query ("18 players · 4 games · 11 trainings"). */
data class FullStatsTable(val rows: List<FullStatsRow>, val games: Int, val trainings: Int) {
    val isEmpty: Boolean get() = games == 0 && trainings == 0
}

fun FullStatsData.table(query: FullStatsQuery, timeZone: TimeZone = TimeZone.currentSystemDefault()): FullStatsTable {
    fun inRange(date: LocalDate) = (query.from == null || date >= query.from) && (query.to == null || date <= query.to)

    val teamId = query.teamId
    val matchesInQuery = matches.filter {
        (teamId == null || it.teamId == teamId) &&
            query.type.includes(it.matchType) &&
            inRange(it.dateTime.toLocalDateTime(timeZone).date)
    }.associateBy { it.matchId }
    val trainingsInQuery = trainings.filter { inRange(it.date) }

    // The players who played for the team this season, whatever the query; the age group's whole pool otherwise.
    val members = if (teamId == null) players else {
        val teamMatchIds = matches.filter { it.teamId == teamId }.map { it.matchId }.toSet()
        val playedIds = lines.filter { it.played && it.matchId in teamMatchIds }.map { it.playerId }.toSet()
        players.filter { it.id in playedIds }
    }

    val linesByPlayer = lines.filter { it.played && it.matchId in matchesInQuery }.groupBy { it.playerId }
    // A session the player missed (Absent, 0) counts as a 0 in their averages: 8, 8 and Absent is 5.3,
    // so missing training lowers the ranking. Only sessions the player was rated in count.
    val trainingByPlayer = trainingsInQuery.flatMap { it.ratings }.groupBy({ it.playerId }, { it.rating })

    val rows = members.map { player ->
        val played = linesByPlayer[player.id].orEmpty()
        val results = played.mapNotNull { matchesInQuery[it.matchId]?.result() }
        val gameRatings = if (canSeeRatings) played.mapNotNull { it.rating?.toDouble() } else emptyList()
        val trainingRatings = if (canSeeRatings) trainingByPlayer[player.id].orEmpty() else emptyList()
        FullStatsRow(
            player = player,
            games = played.size,
            goals = played.sumOf { it.goals },
            assists = played.sumOf { it.assists },
            minutes = played.sumOf { it.minutesPlayed },
            gamesRating = gameRatings.averageOrNull(),
            trainingRating = trainingRatings.averageOrNull(),
            // Every rating in the period counts once, games and trainings together.
            finalRating = (gameRatings + trainingRatings).averageOrNull(),
            ratings = gameRatings.size + trainingRatings.size,
            won = results.count { it == FormResult.WIN },
            drawn = results.count { it == FormResult.DRAW },
            lost = results.count { it == FormResult.LOSS },
            yellowCards = played.sumOf { it.yellowCards },
            redCards = played.sumOf { it.redCards },
            shots = played.sumOf { it.shots },
            shotsOnGoal = played.sumOf { it.shotsOnGoal },
            fouls = played.sumOf { it.fouls }
        )
    }
    return FullStatsTable(rows, games = matchesInQuery.size, trainings = trainingsInQuery.size)
}

/**
 * Sorted by [column]; players with no value there (unrated) always go last, ties by name.
 * A null [column] sorts by the player's name (A to Z, or Z to A when [descending]).
 */
fun List<FullStatsRow>.sortedBy(column: FullStatsColumn?, descending: Boolean): List<FullStatsRow> {
    if (column == null) {
        val byName = compareBy<FullStatsRow> { it.player.name.lowercase() }
        return sortedWith(if (descending) byName.reversed() else byName)
    }
    val byValue = compareBy<FullStatsRow> { it.value(column) == null }
        .then(if (descending) compareByDescending { it.value(column) } else compareBy { it.value(column) })
    return sortedWith(byValue.thenBy { it.player.name })
}

/**
 * The age group's leaders (Age Group → Stats), like a team's ([TeamSeasonRecord.leaders]): the top
 * five on goals, assists and minutes, and on the ranking -- here the final ranking, games and trainings
 * together, with at least [MIN_RATED_MATCHES_FOR_LEADERS] ratings. Players on zero are left out; ties
 * share a rank and are listed by name. The ranking is empty without rating access.
 */
fun FullStatsTable.leaders(): TeamLeaders {
    fun board(value: (FullStatsRow) -> Double?): List<LeaderEntry> {
        val scored = rows.mapNotNull { row -> value(row)?.takeIf { it > 0.0 }?.let { row to it } }
            .sortedWith(compareByDescending<Pair<FullStatsRow, Double>> { it.second }.thenBy { it.first.player.name })
        return scored.map { (row, v) ->
            // Standard competition ranking: 1, 1, 1, 4 ...
            LeaderEntry(scored.indexOfFirst { it.second == v } + 1, row.player.id, row.player.name, v, row.player.photoUrl)
        }.take(LEADERS_SHOWN)
    }
    return TeamLeaders(
        goals = board { it.goals.toDouble() },
        assists = board { it.assists.toDouble() },
        minutes = board { it.minutes.toDouble() },
        averageRating = board { row -> row.finalRating?.takeIf { row.ratings >= MIN_RATED_MATCHES_FOR_LEADERS } }
    )
}

private fun FullStatsMatch.result(): FormResult = when {
    ourScore > opponentScore -> FormResult.WIN
    ourScore < opponentScore -> FormResult.LOSS
    else -> FormResult.DRAW
}

private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()?.let { (it * 10).roundToInt() / 10.0 }
