package com.sportssoftw.squadcall.data.local

import com.sportssoftw.squadcall.domain.repository.UiPreferencesRepository
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

class UiPreferencesRepositoryImpl(private val settings: Settings) : UiPreferencesRepository {
    override fun get(key: String): String? = settings.getStringOrNull(PREFIX + key)
    override fun set(key: String, value: String) {
        settings[PREFIX + key] = value
    }

    private companion object {
        const val PREFIX = "ui_pref_"
    }
}
