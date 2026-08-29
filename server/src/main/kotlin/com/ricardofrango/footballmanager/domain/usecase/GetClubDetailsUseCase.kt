package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.Club
import com.ricardofrango.footballmanager.model.Role

class GetClubDetailsUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, clubId: String): Club {
        // Everyone who is a member of the club (Player, Parent, etc.) can see details
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minRole = Role.PLAYER,
            errorMessage = "You do not have access to this club"
        )

        return clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")
    }
}
