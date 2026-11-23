package com.sportssoftw.squadify.domain.repository

import com.sportssoftw.squadify.model.ChangePasswordRequest
import com.sportssoftw.squadify.model.EmailChangeConfirmRequest
import com.sportssoftw.squadify.model.EmailChangeRequest
import com.sportssoftw.squadify.model.UpdateProfileRequest
import com.sportssoftw.squadify.model.User

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
