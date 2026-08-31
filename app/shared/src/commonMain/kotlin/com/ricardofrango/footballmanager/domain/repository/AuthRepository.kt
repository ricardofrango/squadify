package com.ricardofrango.footballmanager.domain.repository

import com.ricardofrango.footballmanager.model.LoginRequest
import com.ricardofrango.footballmanager.model.LoginResponse

interface AuthRepository {
    suspend fun login(request: LoginRequest): LoginResponse
}
