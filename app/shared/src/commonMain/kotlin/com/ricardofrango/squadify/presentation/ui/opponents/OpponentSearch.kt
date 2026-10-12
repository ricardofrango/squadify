package com.ricardofrango.squadify.presentation.ui.opponents

import com.ricardofrango.squadify.model.Opponent

// Search and grouping for the Opponents list (design 38i): case- and accent-insensitive, on the name
// and the short name, so "academica" finds "Académica".

private val accents = mapOf(
    'á' to 'a', 'à' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a', 'å' to 'a',
    'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
    'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
    'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'õ' to 'o', 'ö' to 'o',
    'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
    'ç' to 'c', 'ñ' to 'n', 'ý' to 'y', 'ÿ' to 'y'
)

/** Lowercase, without accents: what searching and sorting compare. */
internal fun String.searchKey(): String = lowercase().map { accents[it] ?: it }.joinToString("")

/** The opponents matching [query] on name or short name, in the order given. A blank query matches all. */
internal fun filterOpponents(opponents: List<Opponent>, query: String): List<Opponent> {
    val q = query.trim().searchKey()
    if (q.isEmpty()) return opponents
    return opponents.filter { it.name.searchKey().contains(q) || it.shortName?.searchKey()?.contains(q) == true }
}

/** Opponents under their name's first letter (accents folded, "#" for anything else), A to Z. */
internal fun groupByLetter(opponents: List<Opponent>): List<Pair<String, List<Opponent>>> =
    opponents
        .sortedBy { it.name.searchKey() }
        .groupBy { o -> o.name.trim().searchKey().firstOrNull()?.takeIf { it in 'a'..'z' }?.uppercase() ?: "#" }
        .toList()
        .sortedBy { (letter, _) -> if (letter == "#") "{" else letter }

/** What the grey logo placeholder shows: the short name, else the first letter of up to three words. */
internal fun opponentInitials(name: String, shortName: String?): String =
    shortName?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
        ?: name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(3).joinToString("") { it.first().uppercase() }
