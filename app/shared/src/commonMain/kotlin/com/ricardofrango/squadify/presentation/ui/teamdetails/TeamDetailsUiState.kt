package com.ricardofrango.squadify.presentation.ui.teamdetails

import com.ricardofrango.squadify.model.FullStatsData
import com.ricardofrango.squadify.model.FullStatsQuery
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.TeamDetails
import com.ricardofrango.squadify.model.TeamLeaders
import com.ricardofrango.squadify.model.TeamSeasonRecord
import com.ricardofrango.squadify.model.leaders
import com.ricardofrango.squadify.model.table

sealed class TeamDetailsUiState {
    data object Loading : TeamDetailsUiState()
    data class Idle(val details: TeamDetails) : TeamDetailsUiState()
    data class Error(val message: String) : TeamDetailsUiState()
}

/** The season record + team leaders cards (design 31) load on their own: an error stays in the card. */
sealed class TeamRecordUiState {
    data object Loading : TeamRecordUiState()
    data object Error : TeamRecordUiState()
    // Not on the space's plan (the coach app's Free plan).
    data object NotInPlan : TeamRecordUiState()
    data class Loaded(
        val record: TeamSeasonRecord,
        val filter: StatsFilter = StatsFilter.ALL,
        // The team's Full stats data, for Team leaders' ranking (games and trainings, a missed
        // training counting as 0). Null until loaded, or if it failed: the ranking is then left out.
        val fullStats: FullStatsData? = null
    ) : TeamRecordUiState() {
        /** Goals, assists and time from the record; the ranking is the team's Full stats final ranking. */
        fun leaders(): TeamLeaders = record.leaders(filter).copy(
            averageRating = fullStats?.table(FullStatsQuery(type = filter, teamId = record.teamId))?.leaders()?.averageRating.orEmpty()
        )
    }
}

/** Team Details' three tabs (design 32). The screen always opens on Matches. */
enum class TeamTab { STATS, MATCHES, PLAYERS }
