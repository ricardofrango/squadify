package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.Club

class GetClubsUseCase(
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String): List<Club> {
        val user = userRepository.findById(userId) ?: return emptyList()
        
        return if (user.isPlatformAdmin) {
            clubRepository.getAllClubs()
        } else {
            clubRepository.getClubsByUser(userId)
        }
    }
}
