package com.ricardofrango.squadify.presentation.ui.clubdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ricardofrango.squadify.domain.usecase.ClubDetails
import com.ricardofrango.squadify.domain.usecase.DisablePlayerUseCase
import com.ricardofrango.squadify.domain.usecase.GetClubDetailsUseCase
import com.ricardofrango.squadify.domain.usecase.ReactivatePlayerUseCase
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.presentation.errorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import squadify.app.shared.generated.resources.Res
import squadify.app.shared.generated.resources.msg_disable_player_failed
import squadify.app.shared.generated.resources.msg_failed_to_load_club_details
import squadify.app.shared.generated.resources.msg_reactivate_player_failed

/** The club's player list, opened from the More panel as its own screen (no bottom bar). */
sealed class ClubPlayersUiState {
    data object Loading : ClubPlayersUiState()
    data class Error(val message: String) : ClubPlayersUiState()

    data class Idle(
        val details: ClubDetails,
        // Defaults to the most recent season (by end date), as the old Players tab did.
        val seasonId: String? = null,
        val ageGroupId: String? = null,
        val query: String = ""
    ) : ClubPlayersUiState() {
        /** The age-group filter's options: those of the selected season (all, with no season). */
        val availableAgeGroups: List<AgeGroup>
            get() = details.ageGroups.filter { seasonId == null || it.seasonId == seasonId }

        /** Players in the selected season / age group whose name matches the search. */
        val players: List<Player>
            get() = filterClubPlayers(details, seasonId, ageGroupId, query)
    }
}

/**
 * Players belong to age-group pools, so the season / age-group filters go through those pools. A
 * player in no pool yet (just registered to the club) has no season context, so they still show under
 * a season filter -- otherwise registering a player looks like it did nothing -- but not under a
 * specific age group.
 */
internal fun filterClubPlayers(details: ClubDetails, seasonId: String?, ageGroupId: String?, query: String): List<Player> {
    val q = query.trim().lowercase()
    return details.players.filter { player ->
        val pools = details.ageGroupPlayers.filter { it.playerId == player.id }.map { it.ageGroupId }.toSet()
        val matchesAgeGroup = ageGroupId == null || ageGroupId in pools
        val matchesSeason = when {
            seasonId == null -> true
            pools.isEmpty() -> true
            else -> details.ageGroups.any { it.id in pools && it.seasonId == seasonId }
        }
        val matchesSearch = q.isEmpty() || "${player.firstName} ${player.lastName}".lowercase().contains(q)
        matchesSeason && matchesAgeGroup && matchesSearch
    }.sortedBy { !it.active } // Disabled players last, otherwise in the club's order.
}

/** The club after [playerId] was disabled: marked inactive and out of every age group's pool. */
internal fun ClubDetails.withPlayerDisabled(playerId: String): ClubDetails = copy(
    players = players.map { if (it.id == playerId) it.copy(active = false) else it },
    ageGroupPlayers = ageGroupPlayers.filter { it.playerId != playerId }
)

/** The club after [playerId] was reactivated: active again, still in no pool until someone adds them. */
internal fun ClubDetails.withPlayerReactivated(playerId: String): ClubDetails = copy(
    players = players.map { if (it.id == playerId) it.copy(active = true) else it }
)

class ClubPlayersViewModel(
    private val getClubDetailsUseCase: GetClubDetailsUseCase,
    private val disablePlayerUseCase: DisablePlayerUseCase,
    private val reactivatePlayerUseCase: ReactivatePlayerUseCase
) : ViewModel() {

    // One-off results of disabling a player, for the snackbar: the name, or the error (Failure).
    private val _disabled = MutableSharedFlow<Result<String>>()
    val disabled: SharedFlow<Result<String>> = _disabled.asSharedFlow()

    // Likewise for reactivating one.
    private val _reactivated = MutableSharedFlow<Result<String>>()
    val reactivated: SharedFlow<Result<String>> = _reactivated.asSharedFlow()

    private val _state = MutableStateFlow<ClubPlayersUiState>(ClubPlayersUiState.Loading)
    val state: StateFlow<ClubPlayersUiState> = _state.asStateFlow()

    private var loadedClubId: String? = null

    /** Loads once per club; [force] reloads (e.g. back from adding players), keeping the filters. */
    fun load(clubId: String, force: Boolean = false) {
        if (!force && loadedClubId == clubId) return
        loadedClubId = clubId
        val previous = _state.value as? ClubPlayersUiState.Idle
        viewModelScope.launch {
            if (previous == null) _state.value = ClubPlayersUiState.Loading
            _state.value = try {
                val details = getClubDetailsUseCase(clubId)
                previous?.copy(details = details)
                    ?: ClubPlayersUiState.Idle(details, seasonId = details.seasons.maxByOrNull { it.endDate }?.id)
            } catch (e: Exception) {
                ClubPlayersUiState.Error(errorMessage(e, Res.string.msg_failed_to_load_club_details))
            }
        }
    }

    /** Disables [playerId] (coordinators and admins); the row stays, shown as disabled. */
    fun disable(playerId: String) {
        val player = (_state.value as? ClubPlayersUiState.Idle)?.details?.players?.firstOrNull { it.id == playerId } ?: return
        viewModelScope.launch {
            try {
                disablePlayerUseCase(playerId)
                update { it.copy(details = it.details.withPlayerDisabled(playerId)) }
                _disabled.emit(Result.success("${player.firstName} ${player.lastName}"))
            } catch (e: Exception) {
                _disabled.emit(Result.failure(Exception(errorMessage(e, Res.string.msg_disable_player_failed))))
            }
        }
    }

    /** Reactivates a disabled [playerId] (coordinators and admins); they can then be added to an age group. */
    fun reactivate(playerId: String) {
        val player = (_state.value as? ClubPlayersUiState.Idle)?.details?.players?.firstOrNull { it.id == playerId } ?: return
        viewModelScope.launch {
            try {
                reactivatePlayerUseCase(playerId)
                update { it.copy(details = it.details.withPlayerReactivated(playerId)) }
                _reactivated.emit(Result.success("${player.firstName} ${player.lastName}"))
            } catch (e: Exception) {
                _reactivated.emit(Result.failure(Exception(errorMessage(e, Res.string.msg_reactivate_player_failed))))
            }
        }
    }

    fun onSeasonSelected(seasonId: String?) = update { it.copy(seasonId = seasonId, ageGroupId = null) }
    fun onAgeGroupSelected(ageGroupId: String?) = update { it.copy(ageGroupId = ageGroupId) }
    fun onSearchChanged(query: String) = update { it.copy(query = query) }

    private fun update(block: (ClubPlayersUiState.Idle) -> ClubPlayersUiState.Idle) {
        _state.update { (it as? ClubPlayersUiState.Idle)?.let(block) ?: it }
    }
}
