package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.CreateOpponentRequest
import com.ricardofrango.footballmanager.model.Opponent
import com.ricardofrango.footballmanager.model.Role
import kotlin.uuid.Uuid

class CreateOpponentUseCase(
    private val opponentRepository: OpponentRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, request: CreateOpponentRequest): Opponent {
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
            throw ForbiddenException("Only admins or coordinators can create opponents")
        }

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
