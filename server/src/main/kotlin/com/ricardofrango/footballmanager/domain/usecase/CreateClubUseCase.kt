package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.ValidationException
import com.ricardofrango.footballmanager.model.Club

class CreateClubUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserIsAdminUseCase: CheckUserIsAdminUseCase
) {
    suspend operator fun invoke(userId: String, club: Club): Club {
        checkUserIsAdminUseCase(userId).takeIf { it }
            ?: throw ForbiddenException("Only platform admins can perform this action")

        if (club.name.isBlank()) {
            throw ValidationException("Club name cannot be empty")
        }

        return clubRepository.createClub(club)
    }
}
