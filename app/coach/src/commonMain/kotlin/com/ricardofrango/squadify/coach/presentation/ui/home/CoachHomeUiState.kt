package com.ricardofrango.squadify.coach.presentation.ui.home

import com.ricardofrango.squadify.coach.presentation.ui.space.clubNameOf
import com.ricardofrango.squadify.model.SpaceAgeGroups

/**
 * The clubs a space's age groups play for, as its card names them: each club's name (the age group's
 * name's part before " - ") once, in alphabetical order, with the first crest found for it.
 */
data class SpaceClub(val name: String, val logoUrl: String?)

fun clubsOf(space: SpaceAgeGroups): List<SpaceClub> =
    space.ageGroups.mapNotNull { ageGroup -> clubNameOf(ageGroup)?.let { it to ageGroup.logoUrl } }
        .groupBy({ it.first.lowercase() }, { it })
        .map { (_, named) -> SpaceClub(named.first().first, named.firstNotNullOfOrNull { it.second }) }
        .sortedBy { it.name.lowercase() }

sealed class CoachHomeUiState {
    data object Loading : CoachHomeUiState()
    data object Failed : CoachHomeUiState()

    /**
     * The coach's spaces (design "Os meus espaços"): [ownSpace], null until they create their first age
     * groups, and the [otherSpaces] they're staff in, in the server's order. [userName] names their space
     * before it exists ("Espaço de Ana Silva").
     */
    data class Loaded(
        val ownSpace: SpaceAgeGroups?,
        val otherSpaces: List<SpaceAgeGroups>,
        val userName: String? = null
    ) : CoachHomeUiState() {
        companion object {
            fun of(spaces: List<SpaceAgeGroups>, userName: String? = null) =
                Loaded(spaces.firstOrNull { it.isOwner }, spaces.filterNot { it.isOwner }, userName)
        }
    }
}

sealed class CoachHomeNavigationEvent {
    data object NavigateToLogin : CoachHomeNavigationEvent()
}
