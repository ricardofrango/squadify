package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.model.Club

class CreateClubUseCase(private val repository: ClubRepository) {
    suspend operator fun invoke(club: Club): Result<Boolean> {
        if (club.name.isBlank()) {
            return Result.failure(IllegalArgumentException("Club name cannot be empty"))
        }
        
        return try {
            val success = repository.createClub(club)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
