package com.ricardofrango.squadify.presentation.ui.agegroupdetails

import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupSeasonRecord
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.TrainingPlans
import com.ricardofrango.squadify.model.TrainingSessions

sealed class AgeGroupDetailsUiState {
    data object Loading : AgeGroupDetailsUiState()
    data class Idle(val details: AgeGroupDetails) : AgeGroupDetailsUiState()
    data class Error(val message: String) : AgeGroupDetailsUiState()
}

/** What removing a player from the pool came to, for the snackbar. */
sealed class PlayerRemovalResult {
    data class Removed(val playerName: String, val ageGroupName: String) : PlayerRemovalResult()
    data class Failed(val message: String) : PlayerRemovalResult()
}

/** Age Group Details' tabs (design 33, Training from design 40). It always opens on Stats. */
enum class AgeGroupTab { STATS, TEAMS, MATCHES, TRAINING, MEMBERS }

/**
 * The Training tab's plans (design 40a). [Hidden] when the viewer can't read them (players and
 * parents never see plans).
 */
sealed class AgeGroupPlansUiState {
    data object Loading : AgeGroupPlansUiState()
    data object Error : AgeGroupPlansUiState()
    data object Hidden : AgeGroupPlansUiState()
    data class Loaded(val plans: TrainingPlans) : AgeGroupPlansUiState()
}

/** The Training tab's sessions (design 39a), visible to players and parents too. */
sealed class AgeGroupSessionsUiState {
    data object Loading : AgeGroupSessionsUiState()
    data object Error : AgeGroupSessionsUiState()
    data class Loaded(val sessions: TrainingSessions) : AgeGroupSessionsUiState()
}

/** Every team's season record + the pool (Stats and Members tabs); loads on its own so errors stay local. */
sealed class AgeGroupRecordUiState {
    data object Loading : AgeGroupRecordUiState()
    data object Error : AgeGroupRecordUiState()
    data class Loaded(val record: AgeGroupSeasonRecord, val filter: StatsFilter = StatsFilter.ALL) : AgeGroupRecordUiState()
}
