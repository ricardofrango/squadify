package com.ricardofrango.squadify.presentation.ui.opponents

import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.model.SearchText

// Search and grouping for the Opponents list (design 38i): case- and accent-insensitive, on the name
// and the short name, so "academica" finds "Académica".

/** Lowercase, without accents: what searching and sorting compare. */
internal fun String.searchKey(): String = SearchText.key(this)

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
