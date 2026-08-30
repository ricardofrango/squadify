package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.CreateOpponentRequest
import com.ricardofrango.footballmanager.model.Opponent
import kotlin.uuid.Uuid

class CreateOpponentUseCase(
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        request: CreateOpponentRequest
    ): Opponent {
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

        return opponentRepository.createOpponent(opponent)
    }
}
