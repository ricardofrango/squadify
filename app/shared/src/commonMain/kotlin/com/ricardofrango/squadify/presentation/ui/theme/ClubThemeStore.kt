package com.ricardofrango.squadify.presentation.ui.theme

import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The last known colours of every club the app has loaded, by club id -- fed by My clubs, the club
 * screen and Club settings, read by navigation to draw each club's screens in its colours (§57).
 */
class ClubThemeStore {
    private val _themes = MutableStateFlow<Map<String, ClubTheme?>>(emptyMap())
    val themes: StateFlow<Map<String, ClubTheme?>> = _themes.asStateFlow()

    fun remember(clubs: List<Club>) {
        if (clubs.isEmpty()) return
        _themes.update { it + clubs.associate { club -> club.id to club.theme } }
    }

    fun remember(club: Club) = remember(listOf(club))

    fun set(clubId: String, theme: ClubTheme?) {
        _themes.update { it + (clubId to theme) }
    }
}
