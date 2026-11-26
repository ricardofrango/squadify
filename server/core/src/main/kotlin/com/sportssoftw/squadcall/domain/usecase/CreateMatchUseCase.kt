package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.GameScope
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.service.ClubMatchesBus
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.*
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
            val club = clubRepository.getClubById(team.clubId) ?: throw NotFoundException("Club not found")
            // The team's count unless another is picked; kept on the match from now on.
            val teamSize = request.teamSize?.also { size -> requireMatchTeamSize(size, club) } ?: team.teamSize ?: 11

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
                teamSize = teamSize,
                // The club's sport, kept on the match (V33).
                sport = club.sport
            )

            matchRepository.createMatch(match)
        }

        // Sent after the transaction commits -- a failed/slow push must never roll back the match.
        notifyMatchEventUseCase(created.id, MatchNotificationEvent.SCHEDULED, causedBy = userId)
        clubMatchesBus.publish(created.clubId, ClubMatchesUpdate(match = created))

        return created
    }
}
