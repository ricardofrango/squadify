package com.ricardofrango.squadify.domain.repository

interface FcmTokenRepository {
    suspend fun registerToken(token: String)
    suspend fun removeToken(token: String)
}
