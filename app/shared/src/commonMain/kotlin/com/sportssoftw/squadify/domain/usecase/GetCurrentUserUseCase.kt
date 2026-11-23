package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.UserRepository

class GetCurrentUserUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke() = userRepository.getCurrentUser()
}
