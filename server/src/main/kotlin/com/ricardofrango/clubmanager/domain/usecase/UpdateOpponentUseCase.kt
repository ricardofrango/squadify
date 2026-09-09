package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.OpponentRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.NotFoundException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.UpdateOpponentRequest

class UpdateOpponentUseCase(
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        opponentId: String,
        request: UpdateOpponentRequest
    ): Boolean = transactionManager.runInTransaction {
        val existingOpponent = opponentRepository.getOpponentById(opponentId)
            ?: throw NotFoundException("Opponent not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = existingOpponent.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can update opponents"
        )

        val updatedOpponent = existingOpponent.copy(
            name = request.name ?: existingOpponent.name,
            shortName = request.shortName ?: existingOpponent.shortName,
            logoUrl = request.logoUrl ?: existingOpponent.logoUrl
        )

        opponentRepository.updateOpponent(updatedOpponent)
    }
}
