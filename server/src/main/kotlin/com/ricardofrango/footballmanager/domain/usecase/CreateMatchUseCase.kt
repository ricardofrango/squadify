package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.*
import kotlin.uuid.Uuid

class CreateMatchUseCase(
    private val matchRepository: MatchRepository,
    private val teamRepository: TeamRepository,
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        request: CreateMatchRequest
    ): Match = transactionManager.runInTransaction {
        // 1. Verify team exists first to get context
        val team =
            teamRepository.getTeamById(request.teamId) ?: throw NotFoundException("Team not found")

        // 2. Single granular permission check using team's clubId
        checkUserPermissionUseCase(
            userId = userId,
            clubId = team.clubId,
            minTeamRole = TeamRole.COACH,
            teamId = request.teamId,
            errorMessage = "Only admins, coordinators, or team coaches can create matches"
        )

        val opponent = opponentRepository.getOpponentById(request.opponentId)
            ?: throw NotFoundException("Opponent not found")
        
        if (opponent.clubId != team.clubId) {
            throw ForbiddenException("Opponent does not belong to this club")
        }

        val match = Match(
            id = Uuid.random().toString(),
            clubId = team.clubId,
            teamId = request.teamId,
            opponentId = request.opponentId,
            location = request.location,
            dateTime = request.dateTime,
            status = MatchStatus.SCHEDULED,
            notes = request.notes
        )

        matchRepository.createMatch(match)
    }
}
