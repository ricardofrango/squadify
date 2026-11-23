package com.sportssoftw.squadify.domain.repository

/** Small per-device UI choices worth remembering across launches (e.g. Team Details' last tab). */
interface UiPreferencesRepository {
    fun get(key: String): String?
    fun set(key: String, value: String)
}
