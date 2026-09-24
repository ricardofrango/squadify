package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.model.UpdateProfileRequest

class UpdateProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(request: UpdateProfileRequest) = userRepository.updateProfile(request)
}
