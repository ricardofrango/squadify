package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.GameScope
import com.sportssoftw.squadcall.domain.access.allowed
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.MatchEventRepository
import com.sportssoftw.squadcall.domain.repository.MatchRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.MatchStatus
import com.sportssoftw.squadcall.model.OpponentDetails

/**
 * Opponent details (design 38f): the opponent, every match of the club against them with the scores of
 * those that started, and the teams and seasons the client needs for the head-to-head of any season.
 * Readable by any club member, like the Opponents list; [OpponentDetails.canEdit] follows `EDIT_OPPONENT`.
 */
class GetOpponentDetailsUseCase(
    private val opponentRepository: OpponentRepository,
    private val clubRepository: ClubRepository,
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val teamRepository: TeamRepository,
    private val seasonRepository: SeasonRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, opponentId: String): OpponentDetails = transactionManager.runInTransaction {
        val opponent = opponentRepository.getOpponentById(opponentId) ?: throw NotFoundException("Opponent not found")

        accessPolicy.requireMember(userId, opponent.clubId)

        val canEdit = allowed { accessPolicy.requireEditOpponent(userId, GameScope(opponent.clubId)) }
        val canDelete = allowed { accessPolicy.requireDeleteOpponent(userId, GameScope(opponent.clubId)) }

        val club = clubRepository.getClubById(opponent.clubId) ?: throw NotFoundException("Club not found")
        val matches = matchRepository.getMatchesByOpponent(opponentId)
        val started = matches.filter { it.status in STARTED }.map { it.id }
        val teamIds = matches.map { it.teamId }.toSet()

        OpponentDetails(
            opponent = opponent,
            clubName = club.name,
            clubLogoUrl = club.logoUrl,
            matches = matches,
            scores = matchEventRepository.getScoresByMatches(started).values.toList(),
            teams = teamRepository.getTeamsByClub(opponent.clubId).filter { it.id in teamIds },
            seasons = seasonRepository.getSeasonsByClub(opponent.clubId),
            canEdit = canEdit,
            canDelete = canDelete
        )
    }

    private companion object {
        val STARTED = setOf(MatchStatus.LIVE, MatchStatus.HALF_TIME, MatchStatus.FINISHED)
    }
}
