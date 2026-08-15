package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Club

class CreateClubUseCase(private val repository: ClubRepository) {
    suspend operator fun invoke(club: Club): Boolean {
        if (club.name.isBlank()) {
            throw ValidationException("Club name cannot be empty")
        }
        
        return repository.createClub(club)
    }
}
