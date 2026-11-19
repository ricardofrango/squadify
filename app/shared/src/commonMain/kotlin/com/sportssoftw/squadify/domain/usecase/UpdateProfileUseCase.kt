package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.model.UpdateProfileRequest

class UpdateProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(request: UpdateProfileRequest) = userRepository.updateProfile(request)
}
