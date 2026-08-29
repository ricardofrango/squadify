package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.model.Club

class GetClubsUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserIsAdminUseCase: CheckUserIsAdminUseCase
) {
    suspend operator fun invoke(userId: String): List<Club> {
        val isAdmin = checkUserIsAdminUseCase(userId)
        
        return if (isAdmin) {
            clubRepository.getAllClubs()
        } else {
            clubRepository.getClubsByUser(userId)
        }
    }
}
