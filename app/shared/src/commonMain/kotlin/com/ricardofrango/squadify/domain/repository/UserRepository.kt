package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.ChangePasswordRequest
import com.ricardofrango.squadify.model.UpdateProfileRequest
import com.ricardofrango.squadify.model.User

interface UserRepository {
    suspend fun getCurrentUser(): User
    suspend fun updateProfile(request: UpdateProfileRequest): User
    suspend fun changePassword(request: ChangePasswordRequest)
}
