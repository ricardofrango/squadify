package com.ricardofrango.squadify.data.local

import com.ricardofrango.squadify.domain.repository.SessionRepository
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

class SessionRepositoryImpl(private val settings: Settings) : SessionRepository {
    companion object {
        private const val KEY_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }

    override fun saveToken(token: String) {
        settings[KEY_TOKEN] = token
    }

    override fun getToken(): String? {
        return settings.getStringOrNull(KEY_TOKEN)
    }

    override fun saveRefreshToken(token: String) {
        settings[KEY_REFRESH_TOKEN] = token
    }

    override fun getRefreshToken(): String? {
        return settings.getStringOrNull(KEY_REFRESH_TOKEN)
    }

    override fun clearSession() {
        settings.remove(KEY_TOKEN)
        settings.remove(KEY_REFRESH_TOKEN)
    }
}
