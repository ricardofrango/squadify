package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.model.UpdateProfileRequest

class UpdateProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(request: UpdateProfileRequest) = userRepository.updateProfile(request)
}
