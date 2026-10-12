package com.ricardofrango.squadify.presentation.ui.opponents

import com.ricardofrango.squadify.model.MatchStatus
import com.ricardofrango.squadify.model.OpponentDetails
import com.ricardofrango.squadify.model.Season
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.TeamRecordTotals
import com.ricardofrango.squadify.model.TeamSeasonMatch
import com.ricardofrango.squadify.model.TeamSeasonRecord
import com.ricardofrango.squadify.model.totals
import kotlinx.datetime.LocalDate

// Opponent details' head-to-head (design 38i): one season at a time, across all of our teams.

/** The seasons the selector offers, newest first: those with a match against them, plus [defaultId]. */
internal fun headToHeadSeasons(details: OpponentDetails, defaultId: String?): List<Season> {
    val seasonOfTeam = details.teams.associate { it.id to it.seasonId }
    val withMatches = details.matches.mapNotNull { seasonOfTeam[it.teamId] }.toSet()
    return details.seasons
        .filter { it.id in withMatches || it.id == defaultId }
        .sortedByDescending { it.endDate }
}

/** The season the head-to-head opens on: the one [today] falls in, else the latest one. */
internal fun defaultHeadToHeadSeasonId(seasons: List<Season>, today: LocalDate): String? =
    (seasons.firstOrNull { today >= it.startDate && today <= it.endDate } ?: seasons.maxByOrNull { it.endDate })?.id

/** Whether any match against them has finished, in any season. */
internal fun hasFinishedMatches(details: OpponentDetails): Boolean =
    details.matches.any { it.status == MatchStatus.FINISHED }

/**
 * Played, W/D/L, goals and the last five of the finished matches against them in [seasonId].
 * [TeamRecordTotals.form] is most recent first; the screen shows it oldest first.
 */
internal fun headToHead(details: OpponentDetails, seasonId: String?): TeamRecordTotals {
    val seasonOfTeam = details.teams.associate { it.id to it.seasonId }
    val scores = details.scores.associateBy { it.matchId }
    val rows = details.matches
        .filter { it.status == MatchStatus.FINISHED && seasonOfTeam[it.teamId] == seasonId }
        .mapNotNull { match ->
            val score = scores[match.id] ?: return@mapNotNull null
            TeamSeasonMatch(match.id, match.dateTime, details.opponent.name, match.type, score.ourScore, score.opponentScore)
        }
    return TeamSeasonRecord(
        teamId = "",
        seasonId = seasonId.orEmpty(),
        seasonName = "",
        matches = rows,
        playerLines = emptyList(),
        players = emptyList(),
        canSeeRatings = false
    ).totals(StatsFilter.ALL)
}

/** Matches counted in the header's "all-time" figure: every one but the cancelled. */
internal fun allTimeMatchCount(details: OpponentDetails): Int =
    details.matches.count { it.status != MatchStatus.CANCELLED }
