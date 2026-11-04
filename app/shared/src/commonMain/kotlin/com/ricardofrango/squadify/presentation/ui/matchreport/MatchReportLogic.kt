package com.ricardofrango.squadify.presentation.ui.matchreport

import com.ricardofrango.squadify.model.MatchPlayerStatsWithPlayer
import com.ricardofrango.squadify.presentation.ui.matchdetails.CallUpSort
import com.ricardofrango.squadify.presentation.ui.matchdetails.playerComparator
import kotlin.math.roundToInt

/**
 * The four colour bands a 0-10 rating is shown in (design 25d). Bands rather than a gradient, so the
 * colour stays readable in sunlight; the number is always printed too.
 */
enum class RatingBand { LOW, MID, GOOD, TOP }

fun ratingBand(rating: Int): RatingBand = when {
    rating <= 4 -> RatingBand.LOW
    rating <= 6 -> RatingBand.MID
    rating <= 8 -> RatingBand.GOOD
    else -> RatingBand.TOP
}

/** The team sheet: by shirt number (players without one last), then name. The read view's Number order. */
fun teamSheetOrder(rows: List<MatchPlayerStatsWithPlayer>): List<MatchPlayerStatsWithPlayer> =
    rows.sortedWith(
        compareBy<MatchPlayerStatsWithPlayer>({ it.player.shirtNumber ?: Int.MAX_VALUE }, { it.player.lastName.lowercase() }, { it.player.firstName.lowercase() })
    )

/** The editor's Players tab opens sorted by name, as Rate players and the match record's minutes. */
val DEFAULT_RATING_SORT = CallUpSort.NAME

/** The editor's Players tab: by name or position, the call-up's order (§69). Rate next follows it. */
fun ratingOrder(rows: List<MatchPlayerStatsWithPlayer>, sort: CallUpSort = DEFAULT_RATING_SORT): List<MatchPlayerStatsWithPlayer> {
    val byPlayer = playerComparator(sort)
    return rows.sortedWith { a, b -> byPlayer.compare(a.player, b.player) }
}

/**
 * After rating [current], the next player to open: the first unrated player after it in [orderedIds],
 * wrapping around to the start. Null when nobody is left unrated (design 24e: "picking a value ...
 * opens the next unrated player in list order (wrapping around), or closes if none are left").
 */
fun nextUnrated(orderedIds: List<String>, ratings: Map<String, Int?>, current: String?): String? {
    if (orderedIds.isEmpty()) return null
    val start = current?.let { orderedIds.indexOf(it) } ?: -1
    for (step in 1..orderedIds.size) {
        val id = orderedIds[(start + step).mod(orderedIds.size)]
        if (id != current && ratings[id] == null) return id
    }
    return null
}

enum class ReportPlayerSort { RATING, NUMBER }

/**
 * Read-view ordering (design 25d). By rating: high to low, ties by shirt number, unrated last. By
 * number: the team sheet.
 */
fun readOrder(
    rows: List<MatchPlayerStatsWithPlayer>,
    ratings: Map<String, Int?>,
    sort: ReportPlayerSort
): List<MatchPlayerStatsWithPlayer> = when (sort) {
    ReportPlayerSort.NUMBER -> teamSheetOrder(rows)
    ReportPlayerSort.RATING -> teamSheetOrder(rows).sortedWith(
        compareBy<MatchPlayerStatsWithPlayer> { ratings[it.player.id] == null }
            .thenByDescending { ratings[it.player.id] ?: -1 }
    )
}

/** "{rated} of {total} rated · avg {x.xx}" -- the average skips unrated players, null when none are rated. */
data class RatingSummary(val rated: Int, val total: Int, val average: String?)

fun ratingSummary(ratings: List<Int?>): RatingSummary {
    val given = ratings.filterNotNull()
    val average = if (given.isEmpty()) null else {
        // Two decimals, as every ranking (the developer, 2026-11-04).
        val hundredths = (given.sum() * 100.0 / given.size).roundToInt()
        "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }
    return RatingSummary(rated = given.size, total = ratings.size, average = average)
}
