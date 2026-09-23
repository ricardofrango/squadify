package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.*
import kotlin.uuid.Uuid

class CreateMatchUseCase(
    private val matchRepository: MatchRepository,
    private val teamRepository: TeamRepository,
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val notifyMatchEventUseCase: NotifyMatchEventUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        request: CreateMatchRequest
    ): Match {
        val created = transactionManager.runInTransaction {
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

        // Sent after the transaction commits -- a failed/slow push must never roll back the match.
        notifyMatchEventUseCase(created.id, MatchNotificationEvent.SCHEDULED)

        return created
    }
}
