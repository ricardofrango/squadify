package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.UserRepository

class GetCurrentUserUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke() = userRepository.getCurrentUser()
}
