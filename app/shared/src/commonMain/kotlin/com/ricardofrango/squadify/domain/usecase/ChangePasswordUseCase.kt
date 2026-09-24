package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.model.ChangePasswordRequest

class ChangePasswordUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(request: ChangePasswordRequest) = userRepository.changePassword(request)
}
