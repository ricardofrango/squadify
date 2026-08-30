package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.Opponent

class GetOpponentsUseCase(
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Opponent> {
        // 1. Check permissions first (Member of club)
        checkUserPermissionUseCase(userId, clubId, minClubRole = ClubRole.MEMBER)

        return opponentRepository.getOpponentsByClub(clubId)
    }
}
