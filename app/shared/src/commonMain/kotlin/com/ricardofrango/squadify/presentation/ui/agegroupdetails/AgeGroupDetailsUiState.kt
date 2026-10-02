package com.ricardofrango.squadify.presentation.ui.agegroupdetails

import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupSeasonRecord
import com.ricardofrango.squadify.model.StatsFilter

sealed class AgeGroupDetailsUiState {
    data object Loading : AgeGroupDetailsUiState()
    data class Idle(val details: AgeGroupDetails) : AgeGroupDetailsUiState()
    data class Error(val message: String) : AgeGroupDetailsUiState()
}

/** Age Group Details' four tabs (design 33). It always opens on Stats. */
enum class AgeGroupTab { STATS, TEAMS, MATCHES, MEMBERS }

/** Every team's season record + the pool (Stats and Members tabs); loads on its own so errors stay local. */
sealed class AgeGroupRecordUiState {
    data object Loading : AgeGroupRecordUiState()
    data object Error : AgeGroupRecordUiState()
    data class Loaded(val record: AgeGroupSeasonRecord, val filter: StatsFilter = StatsFilter.ALL) : AgeGroupRecordUiState()
}
