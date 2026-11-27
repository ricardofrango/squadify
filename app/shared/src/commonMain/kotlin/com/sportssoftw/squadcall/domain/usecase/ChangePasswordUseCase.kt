package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.UserRepository
import com.sportssoftw.squadcall.model.ChangePasswordRequest

class ChangePasswordUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(request: ChangePasswordRequest) = userRepository.changePassword(request)
}
