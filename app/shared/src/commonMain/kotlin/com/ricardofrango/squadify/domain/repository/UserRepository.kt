package com.ricardofrango.squadify.domain.repository

import com.ricardofrango.squadify.model.ChangePasswordRequest
import com.ricardofrango.squadify.model.EmailChangeConfirmRequest
import com.ricardofrango.squadify.model.EmailChangeRequest
import com.ricardofrango.squadify.model.UpdateProfileRequest
import com.ricardofrango.squadify.model.User

interface UserRepository {
    suspend fun getCurrentUser(): User
    suspend fun updateProfile(request: UpdateProfileRequest): User
    suspend fun changePassword(request: ChangePasswordRequest)
    suspend fun requestEmailChange(request: EmailChangeRequest)
    suspend fun confirmEmailChange(request: EmailChangeConfirmRequest): User
    /** Uploads [bytes] (PNG or JPEG) as the user's own photo; returns the new photo path. */
    suspend fun uploadAvatar(bytes: ByteArray): String
    suspend fun deleteAvatar()
}
