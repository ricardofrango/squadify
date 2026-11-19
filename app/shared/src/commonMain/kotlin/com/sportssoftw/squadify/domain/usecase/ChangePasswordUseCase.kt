package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.UserRepository
import com.sportssoftw.squadify.model.ChangePasswordRequest

class ChangePasswordUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(request: ChangePasswordRequest) = userRepository.changePassword(request)
}
