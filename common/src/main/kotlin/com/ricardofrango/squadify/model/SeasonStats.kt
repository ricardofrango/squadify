package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.time.Instant

// Season stats for Player Details (design 30) and the season record + team leaders on Team Details
// (design 31). The server returns the per-match rows; every total, the W/D/L record, form and the
// leader boards are derived here, from those rows, so switching the All / Official / Tournament /
// Friendly filter (or a player's season) needs no round trip and the server and clients can't disagree.

/** The match-type filter shared by both screens. */
enum class StatsFilter {
    ALL,
    // Official league games (Championship).
    OFFICIAL,
    TOURNAMENT,
    FRIENDLY;

    /** Untyped (older) matches only count under [ALL]. */
    fun includes(type: MatchType?): Boolean = when (this) {
        ALL -> true
        OFFICIAL -> type == MatchType.CHAMPIONSHIP
        TOURNAMENT -> type == MatchType.TOURNAMENT
        FRIENDLY -> type == MatchType.FRIENDLY
    }
}

// ---- Player ----

/** One finished match a player has a stats line for. [rating] is null when unrated or hidden from the caller. */
@Serializable
data class PlayerSeasonMatch(
    val matchId: String,
    val dateTime: Instant,
    val opponentName: String,
    val matchType: MatchType? = null,
    val started: Boolean,
    val minutesPlayed: Int,
    val goals: Int,
    val assists: Int,
    val ownGoals: Int,
    val yellowCards: Int,
    val redCards: Int,
    val rating: Int? = null,
    // The team he played for in that match ("Sub-15 A"), so A and B are never ambiguous.
    val teamName: String = "",
    // Home first in the row's "Team vs Opponent" (MatchVenue.ourSideFirst).
    val venue: MatchVenue = MatchVenue.HOME
)

/**
 * One season a player was registered in. [matches] are newest first. [preStatsMatchCount] counts the
 * finished matches he was called up for before stats were recorded, which have no stats line.
 */
@Serializable
data class PlayerSeason(
    val seasonId: String,
    val seasonName: String,
    val teamNames: List<String>,
    val matches: List<PlayerSeasonMatch>,
    val preStatsMatchCount: Int = 0
)

/**
 * Everything behind Player Details' season stats card. [seasons] are newest first; [currentSeasonId]
 * is the one to open on. [canSeeRatings] is false for players/parents -- the ratings themselves are
 * already stripped server-side, this only tells the UI to drop the rating column.
 */
@Serializable
data class PlayerSeasonStats(
    val playerId: String,
    val seasons: List<PlayerSeason>,
    val currentSeasonId: String? = null,
    val canSeeRatings: Boolean
)

data class PlayerSeasonTotals(
    val apps: Int,
    val starts: Int,
    val minutes: Int,
    val goals: Int,
    val assists: Int,
    val yellowCards: Int,
    val redCards: Int,
    val ratedMatches: Int,
    // Mean of the ratings in the filter, one decimal; null with no rated matches.
    val averageRating: Double?,
    // Matches with no type, in this selection -- only ever non-zero under ALL (design 30a's note).
    val untypedMatches: Int
)

/**
 * An appearance: he started or got at least one minute. Every called-up player has a stats line, so an
 * unused substitute has a 0-minute line that must not count as a match played (design 32a).
 */
val PlayerSeasonMatch.played: Boolean get() = started || minutesPlayed > 0

/** His appearances within [filter] -- the rows behind the totals and the Matches list. */
fun List<PlayerSeasonMatch>.inFilter(filter: StatsFilter): List<PlayerSeasonMatch> =
    filter { it.played && filter.includes(it.matchType) }

fun List<PlayerSeasonMatch>.totals(filter: StatsFilter): PlayerSeasonTotals {
    val rows = inFilter(filter)
    val ratings = rows.mapNotNull { it.rating }
    return PlayerSeasonTotals(
        apps = rows.size,
        starts = rows.count { it.started },
        minutes = rows.sumOf { it.minutesPlayed },
        goals = rows.sumOf { it.goals },
        assists = rows.sumOf { it.assists },
        yellowCards = rows.sumOf { it.yellowCards },
        redCards = rows.sumOf { it.redCards },
        ratedMatches = ratings.size,
        averageRating = ratings.takeIf { it.isNotEmpty() }?.average()?.roundToOneDecimal(),
        untypedMatches = rows.count { it.matchType == null }
    )
}

// ---- Team ----

/** A finished match of the team, with its final score. */
@Serializable
data class TeamSeasonMatch(
    val matchId: String,
    val dateTime: Instant,
    val opponentName: String,
    val matchType: MatchType? = null,
    val ourScore: Int,
    val opponentScore: Int
)

/**
 * One called-up player's line in one of the team's finished matches. [rating] as in
 * [PlayerSeasonMatch]. An unused substitute has a line with 0 minutes and [started] false.
 */
@Serializable
data class TeamSeasonPlayerLine(
    val matchId: String,
    val playerId: String,
    val minutesPlayed: Int,
    val goals: Int,
    val assists: Int,
    val rating: Int? = null,
    val started: Boolean = false
) {
    val played: Boolean get() = started || minutesPlayed > 0
}

@Serializable
data class TeamSeasonPlayer(
    val id: String,
    val name: String,
    val shirtNumber: Int? = null,
    val position: PlayerPosition? = null,
    // The player's photo path, when they have one.
    val photoUrl: String? = null
)

/** Everything behind Team Details' season record and team leaders cards. [matches] newest first. */
@Serializable
data class TeamSeasonRecord(
    val teamId: String,
    val seasonId: String,
    val seasonName: String,
    val matches: List<TeamSeasonMatch>,
    val playerLines: List<TeamSeasonPlayerLine>,
    val players: List<TeamSeasonPlayer>,
    val canSeeRatings: Boolean,
    // For the age group's per-team lists (design 33); Team Details already has it.
    val teamName: String = ""
)

enum class FormResult { WIN, DRAW, LOSS }

data class TeamFormEntry(val matchId: String, val result: FormResult, val match: TeamSeasonMatch)

data class TeamRecordTotals(
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    // The last five in the filter, most recent first.
    val form: List<TeamFormEntry>,
    val untypedMatches: Int
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

fun TeamSeasonMatch.result(): FormResult = when {
    ourScore > opponentScore -> FormResult.WIN
    ourScore < opponentScore -> FormResult.LOSS
    else -> FormResult.DRAW
}

fun TeamSeasonRecord.totals(filter: StatsFilter): TeamRecordTotals {
    val rows = matches.filter { filter.includes(it.matchType) }.sortedByDescending { it.dateTime }
    return TeamRecordTotals(
        played = rows.size,
        won = rows.count { it.result() == FormResult.WIN },
        drawn = rows.count { it.result() == FormResult.DRAW },
        lost = rows.count { it.result() == FormResult.LOSS },
        goalsFor = rows.sumOf { it.ourScore },
        goalsAgainst = rows.sumOf { it.opponentScore },
        form = rows.take(FORM_LENGTH).map { TeamFormEntry(it.matchId, it.result(), it) },
        untypedMatches = rows.count { it.matchType == null }
    )
}

const val FORM_LENGTH = 5
const val LEADERS_SHOWN = 3
// One 9 shouldn't top the rating table (design 31a).
const val MIN_RATED_MATCHES_FOR_LEADERS = 2

/** [value] is the raw number: a count, minutes, or an average rating (one decimal). */
data class LeaderEntry(val rank: Int, val playerId: String, val name: String, val value: Double, val photoUrl: String? = null)

data class TeamLeaders(
    val goals: List<LeaderEntry>,
    val assists: List<LeaderEntry>,
    val minutes: List<LeaderEntry>,
    // Empty when nobody has enough rated matches, or the caller can't see ratings.
    val averageRating: List<LeaderEntry>
)

/**
 * Top three per category within [filter]. Players on zero are left out, so a list can be shorter
 * than three. Ties share a rank (three players on 4 goals are all #1) and are listed by name.
 */
fun TeamSeasonRecord.leaders(filter: StatsFilter): TeamLeaders {
    val matchIds = matches.filter { filter.includes(it.matchType) }.map { it.matchId }.toSet()
    val lines = playerLines.filter { it.matchId in matchIds }.groupBy { it.playerId }
    val names = players.associate { it.id to it.name }
    val photos = players.associate { it.id to it.photoUrl }

    fun board(value: (List<TeamSeasonPlayerLine>) -> Double?): List<LeaderEntry> {
        val scored = lines.mapNotNull { (playerId, rows) ->
            val v = value(rows) ?: return@mapNotNull null
            if (v <= 0.0) null else Triple(playerId, names[playerId] ?: "—", v)
        }.sortedWith(compareByDescending<Triple<String, String, Double>> { it.third }.thenBy { it.second })
        return scored.map { (id, name, v) ->
            // Standard competition ranking: 1, 1, 1, 4 ...
            val rank = scored.indexOfFirst { it.third == v } + 1
            LeaderEntry(rank, id, name, v, photos[id])
        }.take(LEADERS_SHOWN)
    }

    return TeamLeaders(
        goals = board { rows -> rows.sumOf { it.goals }.toDouble() },
        assists = board { rows -> rows.sumOf { it.assists }.toDouble() },
        minutes = board { rows -> rows.sumOf { it.minutesPlayed }.toDouble() },
        averageRating = if (!canSeeRatings) emptyList() else board { rows ->
            val ratings = rows.mapNotNull { it.rating }
            if (ratings.size < MIN_RATED_MATCHES_FOR_LEADERS) null else ratings.average().roundToOneDecimal()
        }
    )
}

// ---- Team squad (Players tab, design 32) ----

/** A player who played for the team within the filter, with his totals there. */
data class SquadEntry(
    val player: TeamSeasonPlayer,
    val apps: Int,
    val minutes: Int,
    val goals: Int,
    val assists: Int,
    val ratedMatches: Int,
    val averageRating: Double?
)

/** Called up for the team within the filter but never on the pitch. */
data class BenchEntry(val player: TeamSeasonPlayer, val calledUp: Int)

data class TeamSquad(val used: List<SquadEntry>, val benchOnly: List<BenchEntry>)

enum class SquadSort { APPS, MINUTES, GOALS, RATING, NAME }

/**
 * The Players tab: only the players who played for THIS team (at least one minute, or a start) within
 * [filter], never the whole age-group pool. Players called up without ever coming on are kept apart in
 * [TeamSquad.benchOnly]. Sorted by [sort], ties by name; the rating sort puts unrated players last.
 */
fun TeamSeasonRecord.squad(filter: StatsFilter, sort: SquadSort = SquadSort.APPS): TeamSquad {
    val matchIds = matches.filter { filter.includes(it.matchType) }.map { it.matchId }.toSet()
    val byPlayer = playerLines.filter { it.matchId in matchIds }.groupBy { it.playerId }
    val playersById = players.associateBy { it.id }

    val used = mutableListOf<SquadEntry>()
    val bench = mutableListOf<BenchEntry>()
    byPlayer.forEach { (playerId, lines) ->
        val player = playersById[playerId] ?: return@forEach
        val played = lines.filter { it.played }
        if (played.isEmpty()) {
            bench += BenchEntry(player, calledUp = lines.size)
        } else {
            val ratings = played.mapNotNull { it.rating }
            used += SquadEntry(
                player = player,
                apps = played.size,
                minutes = played.sumOf { it.minutesPlayed },
                goals = played.sumOf { it.goals },
                assists = played.sumOf { it.assists },
                ratedMatches = ratings.size,
                averageRating = ratings.takeIf { it.isNotEmpty() }?.average()?.roundToOneDecimal()
            )
        }
    }
    val byName = compareBy<SquadEntry> { it.player.name }
    val sorted = when (sort) {
        SquadSort.APPS -> used.sortedWith(compareByDescending<SquadEntry> { it.apps }.then(byName))
        SquadSort.MINUTES -> used.sortedWith(compareByDescending<SquadEntry> { it.minutes }.then(byName))
        SquadSort.GOALS -> used.sortedWith(compareByDescending<SquadEntry> { it.goals }.then(byName))
        SquadSort.RATING -> used.sortedWith(compareByDescending<SquadEntry> { it.averageRating ?: -1.0 }.then(byName))
        SquadSort.NAME -> used.sortedWith(byName)
    }
    return TeamSquad(sorted, bench.sortedWith(compareByDescending<BenchEntry> { it.calledUp }.thenBy { it.player.name }))
}

/** The team's average rating over every rated appearance this season (staff only; null with none). */
fun TeamSeasonRecord.averageRating(): Double? =
    playerLines.filter { it.played }.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average()?.roundToOneDecimal()

/** How many different players played for the team (a start or 1+ minute) this season. */
fun TeamSeasonRecord.playersUsed(): Int = playerLines.filter { it.played }.map { it.playerId }.distinct().size

// ---- Age group (design 33) ----

/**
 * Everything behind Age Group Details' Stats and Members tabs: one [TeamSeasonRecord] per team of the
 * age group, plus the age group's pool. Every number is derived here, client-side.
 */
@Serializable
data class AgeGroupSeasonRecord(
    val ageGroupId: String,
    val teams: List<TeamSeasonRecord>,
    val pool: List<TeamSeasonPlayer>,
    val canSeeRatings: Boolean
)

/** All the age group's finished matches as one record ("Age group · 13 played"). */
fun AgeGroupSeasonRecord.combined(filter: StatsFilter): TeamRecordTotals =
    TeamSeasonRecord(
        teamId = ageGroupId, seasonId = "", seasonName = "",
        matches = teams.flatMap { it.matches }, playerLines = emptyList(), players = emptyList(), canSeeRatings = false
    ).totals(filter)

/** A pool player's minutes band: neutral names, so it reads as a plan, not a verdict (design 33a). */
enum class MinutesBand { LOTS, SOME, LITTLE, NONE }

fun minutesBand(minutes: Int): MinutesBand = when {
    minutes >= 300 -> MinutesBand.LOTS
    minutes >= 100 -> MinutesBand.SOME
    minutes >= 1 -> MinutesBand.LITTLE
    else -> MinutesBand.NONE
}

data class PoolMinutes(
    val totalPlayers: Int,
    // Every pool player in exactly one band, least minutes first within a band, then by name.
    val bands: Map<MinutesBand, List<TeamSeasonPlayer>>,
    // Pool players who played for more than one of the age group's teams.
    val playedForSeveralTeams: Int
)

/**
 * Minutes across the pool within [filter]: a player who played for A and B is counted once, with
 * combined minutes. Players who never came on (or were never called up) are in [MinutesBand.NONE].
 */
fun AgeGroupSeasonRecord.poolMinutes(filter: StatsFilter): PoolMinutes {
    val minutes = mutableMapOf<String, Int>()
    val teamsPlayed = mutableMapOf<String, MutableSet<String>>()
    teams.forEach { team ->
        val matchIds = team.matches.filter { filter.includes(it.matchType) }.map { it.matchId }.toSet()
        team.playerLines.filter { it.matchId in matchIds && it.played }.forEach { line ->
            minutes[line.playerId] = (minutes[line.playerId] ?: 0) + line.minutesPlayed
            teamsPlayed.getOrPut(line.playerId) { mutableSetOf() } += team.teamId
        }
    }
    val bands = pool
        .sortedWith(compareBy<TeamSeasonPlayer> { minutes[it.id] ?: 0 }.thenBy { it.name })
        .groupBy { minutesBand(minutes[it.id] ?: 0) }
    val poolIds = pool.map { it.id }.toSet()
    return PoolMinutes(
        totalPlayers = pool.size,
        bands = MinutesBand.entries.associateWith { bands[it].orEmpty() },
        playedForSeveralTeams = teamsPlayed.count { (id, set) -> id in poolIds && set.size > 1 }
    )
}

/** A pool member's season across the age group's teams (Members tab, design 33). */
data class MemberSeason(val apps: Int, val minutes: Int, val teamIds: List<String>)

/** Every player's season across all the age group's teams (All matches); absent = no apps yet. */
fun AgeGroupSeasonRecord.memberSeasons(): Map<String, MemberSeason> {
    val byPlayer = mutableMapOf<String, MemberSeason>()
    teams.forEach { team ->
        team.playerLines.filter { it.played }.groupBy { it.playerId }.forEach { (playerId, lines) ->
            val previous = byPlayer[playerId]
            byPlayer[playerId] = MemberSeason(
                apps = (previous?.apps ?: 0) + lines.size,
                minutes = (previous?.minutes ?: 0) + lines.sumOf { it.minutesPlayed },
                teamIds = (previous?.teamIds.orEmpty() + team.teamId).distinct()
            )
        }
    }
    return byPlayer
}

private fun Double.roundToOneDecimal(): Double = (this * 10).roundToInt() / 10.0
