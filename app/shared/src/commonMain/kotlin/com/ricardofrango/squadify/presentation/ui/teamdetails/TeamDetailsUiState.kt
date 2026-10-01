package com.ricardofrango.squadify.presentation.ui.teamdetails

import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.TeamDetails
import com.ricardofrango.squadify.model.TeamSeasonRecord

sealed class TeamDetailsUiState {
    data object Loading : TeamDetailsUiState()
    data class Idle(val details: TeamDetails) : TeamDetailsUiState()
    data class Error(val message: String) : TeamDetailsUiState()
}

/** The season record + team leaders cards (design 31) load on their own: an error stays in the card. */
sealed class TeamRecordUiState {
    data object Loading : TeamRecordUiState()
    data object Error : TeamRecordUiState()
    data class Loaded(val record: TeamSeasonRecord, val filter: StatsFilter = StatsFilter.ALL) : TeamRecordUiState()
}
