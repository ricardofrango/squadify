package com.ricardofrango.squadify.model

import kotlinx.serialization.Serializable

/**
 * The lists that grow without bound (the club's matches, players and members) are fetched a page at a
 * time with `limit` and `offset` (TECHNICAL_DECISIONS §5). A request without `limit` still gets the
 * whole list, for app builds from before paging.
 */
object Paging {
    const val PAGE_SIZE = 30
    const val MAX_LIMIT = 100

    /** The page size asked for, kept within 1..[MAX_LIMIT] ([PAGE_SIZE] when unreadable); null asks for no paging. */
    fun limitOf(raw: String?): Int? = raw?.let { (it.toIntOrNull() ?: PAGE_SIZE).coerceIn(1, MAX_LIMIT) }

    fun offsetOf(raw: String?): Int = raw?.toIntOrNull()?.coerceAtLeast(0) ?: 0
}

/** The club Matches tab's two lists: upcoming (soonest first) and finished (newest first). */
@Serializable
enum class MatchListStatus { UPCOMING, FINISHED }

/** One page of the club's matches (`GET /matches` with `limit`). */
@Serializable
data class MatchesPage(
    val matches: List<Match>,
    // The score of each started match on the page, so the list needs no request per match.
    val scores: List<MatchScore> = emptyList(),
    val hasMore: Boolean = false,
    // Whether the viewer can see any match of the club at all, whatever the filters: the tab's full
    // empty state ("No matches yet") rather than "nothing for these filters".
    val anyMatches: Boolean = false
)

/** One page of the club's players (`GET /players` with `limit`). */
@Serializable
data class PlayersPage(
    val players: List<Player>,
    val hasMore: Boolean = false,
    // Whether the viewer can see any player of the club at all, whatever the filters.
    val anyPlayers: Boolean = false
)

/** Case- and accent-insensitive searching ("academica" finds "Académica"), on the device and on the server. */
object SearchText {
    private val accents = mapOf(
        'á' to 'a', 'à' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a', 'å' to 'a',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'õ' to 'o', 'ö' to 'o',
        'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n', 'ý' to 'y', 'ÿ' to 'y'
    )

    // The same folding as two strings of equal length, for SQL's translate().
    val ACCENTED: String = accents.keys.joinToString("")
    val UNACCENTED: String = accents.values.joinToString("")

    /** Lowercase, without accents: what searching and sorting compare. */
    fun key(text: String): String = text.lowercase().map { accents[it] ?: it }.joinToString("")
}
