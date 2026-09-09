package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.ClubRepository
import com.ricardofrango.footballmanager.model.ClubHome

class GetClubHomeUseCase(
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(clubId: String): ClubHome {
        return clubRepository.getClubHome(clubId)
    }
}
