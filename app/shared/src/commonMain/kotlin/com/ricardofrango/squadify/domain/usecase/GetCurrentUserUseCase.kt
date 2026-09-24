package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.UserRepository

class GetCurrentUserUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke() = userRepository.getCurrentUser()
}
