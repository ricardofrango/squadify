package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.TeamRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.*
import kotlin.uuid.Uuid

class CreateMatchUseCase(
    private val matchRepository: MatchRepository,
    private val teamRepository: TeamRepository,
    private val opponentRepository: OpponentRepository,
    private val accessPolicy: AccessPolicy,
    private val notifyMatchEventUseCase: NotifyMatchEventUseCase,
    private val clubMatchesBus: ClubMatchesBus,
    private val transactionManager: TransactionManager,
    private val clubRepository: ClubRepository
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
            accessPolicy.requireManageMatch(userId, GameScope(team.clubId, teamId = request.teamId), "Only admins, coordinators, or team coaches can create matches")

            val opponent = opponentRepository.getOpponentById(request.opponentId)
                ?: throw NotFoundException("Opponent not found")

            if (opponent.clubId != team.clubId) {
                throw ForbiddenException("Opponent does not belong to this club")
            }
            if (request.halves !in 1..2) {
                throw ValidationException("A match is played in 1 or 2 halves")
            }
            // The team's count unless another is picked; kept on the match from now on.
            val teamSize = request.teamSize?.also { size ->
                val club = clubRepository.getClubById(team.clubId) ?: throw NotFoundException("Club not found")
                requireMatchTeamSize(size, club)
            } ?: team.teamSize ?: 11

            val match = Match(
                id = Uuid.random().toString(),
                clubId = team.clubId,
                teamId = request.teamId,
                opponentId = request.opponentId,
                location = request.location,
                dateTime = request.dateTime,
                status = MatchStatus.SCHEDULED,
                notes = request.notes,
                type = request.type,
                halves = request.halves,
                venue = request.venue ?: MatchVenue.HOME,
                teamSize = teamSize
            )

            matchRepository.createMatch(match)
        }

        // Sent after the transaction commits -- a failed/slow push must never roll back the match.
        notifyMatchEventUseCase(created.id, MatchNotificationEvent.SCHEDULED)
        clubMatchesBus.publish(created.clubId, ClubMatchesUpdate(match = created))

        return created
    }
}
