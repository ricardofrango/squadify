package com.ricardofrango.squadify.presentation.ui.agegroupdetails

import com.ricardofrango.squadify.domain.config.AppFeatures
import com.ricardofrango.squadify.model.AgeGroupDetails
import com.ricardofrango.squadify.model.AgeGroupSeasonRecord
import com.ricardofrango.squadify.model.StatsFilter
import com.ricardofrango.squadify.model.TrainingPlans
import com.ricardofrango.squadify.model.TrainingSessions

sealed class AgeGroupDetailsUiState {
    data object Loading : AgeGroupDetailsUiState()
    data class Idle(
        val details: AgeGroupDetails,
        // The coach app's owner: Invite staff in ⋮, and a remove button on each staff row.
        val invitesStaff: Boolean = false
    ) : AgeGroupDetailsUiState()
    data class Error(val message: String) : AgeGroupDetailsUiState()
}

/**
 * The details as this app shows them. Without club members (the coach app), staff don't come from Add
 * staff and a staff name opens no member's page, so `canAddStaff` is turned off; whoever the server lets
 * manage staff (the space's owner) invites them with a code and removes them instead.
 */
fun AgeGroupDetails.idleFor(features: AppFeatures): AgeGroupDetailsUiState.Idle =
    if (features.clubMembers) AgeGroupDetailsUiState.Idle(this)
    else AgeGroupDetailsUiState.Idle(copy(canAddStaff = false), invitesStaff = canAddStaff)

/** What taking someone off the staff came to, for the snackbar. */
sealed class StaffRemovalResult {
    data class Removed(val name: String, val ageGroupName: String) : StaffRemovalResult()
    data class Failed(val message: String) : StaffRemovalResult()
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
    // Not on the space's plan (the coach app's Free plan).
    data object NotInPlan : AgeGroupRecordUiState()
    data class Loaded(val record: AgeGroupSeasonRecord, val filter: StatsFilter = StatsFilter.ALL) : AgeGroupRecordUiState()
}
