package com.ricardofrango.footballmanager.data.remote

import com.ricardofrango.footballmanager.domain.config.AppConfig
import com.ricardofrango.footballmanager.domain.repository.AuthRepository
import com.ricardofrango.footballmanager.model.LoginRequest
import com.ricardofrango.footballmanager.model.LoginResponse
import com.ricardofrango.footballmanager.model.User
import com.ricardofrango.footballmanager.model.UserStatus
import kotlin.time.Instant

class AuthRepositoryImpl(
    private val appConfig: AppConfig
) : AuthRepository {
    
    override suspend fun login(request: LoginRequest): LoginResponse {
        // This is a stub for now as requested. 
        // We will add Ktor and real implementation later.
        println("Logging in to ${appConfig.baseUrl} with ${request.email}")
        
        return LoginResponse(
            accessToken = "dummy-token",
            refreshToken = "dummy-refresh",
            user = User(
                id = "1",
                email = request.email,
                firstName = "Dummy",
                lastName = "User",
                status = UserStatus.ACTIVE,
                createdAt = Instant.fromEpochMilliseconds(0) // Dummy epoch
            )
        )
    }
}
