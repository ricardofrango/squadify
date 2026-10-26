package com.ricardofrango.squadify.presentation.ui.clubdetails

import com.ricardofrango.squadify.model.MatchListStatus

/**
 * Where the Matches tab is in its lists, which load a page at a time: Upcoming (the upcoming
 * matches, then the not-played ones) or Finished alone, or All as all three in turn, so the tab keeps
 * its order, the order they happen in (upcoming soonest first, not played and the results newest first).
 */
internal class MatchesCursor(status: MatchesStatusFilter) {
    private val lists = when (status) {
        MatchesStatusFilter.UPCOMING -> listOf(MatchListStatus.UPCOMING, MatchListStatus.NOT_PLAYED)
        MatchesStatusFilter.FINISHED -> listOf(MatchListStatus.FINISHED)
        MatchesStatusFilter.ALL -> listOf(MatchListStatus.UPCOMING, MatchListStatus.NOT_PLAYED, MatchListStatus.FINISHED)
    }
    private var index = 0

    /** Rows of [list] already loaded: the next page's offset. */
    var offset = 0
        private set

    val hasMore: Boolean get() = index < lists.size

    /** The list the next page comes from. */
    val list: MatchListStatus get() = lists[index]

    /** Moves past a page of [size] rows; [more] says whether its list has another page. */
    fun advance(size: Int, more: Boolean) {
        if (more) {
            offset += size
        } else {
            index++
            offset = 0
        }
    }
}
