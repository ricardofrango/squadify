package com.ricardofrango.clubmanager.domain.repository

interface SessionRepository {
    fun saveToken(token: String)
    fun getToken(): String?
    fun saveRefreshToken(token: String)
    fun getRefreshToken(): String?
    fun clearSession()
}
