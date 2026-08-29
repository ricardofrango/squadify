package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.model.Opponent
import com.ricardofrango.footballmanager.model.Role

class GetOpponentsUseCase(
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, clubId: String): List<Opponent> {
        // 1. Check permissions first (Fail fast)
        checkUserPermissionUseCase(userId, clubId, Role.PLAYER)

        return opponentRepository.getOpponentsByClub(clubId)
    }
}
