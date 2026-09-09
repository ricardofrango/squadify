package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.OpponentRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.CreateOpponentRequest
import com.ricardofrango.clubmanager.model.Opponent
import kotlin.uuid.Uuid

class CreateOpponentUseCase(
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        request: CreateOpponentRequest
    ): Opponent = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can create opponents"
        )

        val opponent = Opponent(
            id = Uuid.random().toString(),
            clubId = clubId,
            name = request.name,
            shortName = request.shortName,
            logoUrl = request.logoUrl
        )

        opponentRepository.createOpponent(opponent)
    }
}
