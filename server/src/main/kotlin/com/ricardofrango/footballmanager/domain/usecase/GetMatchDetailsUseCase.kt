package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.MatchDetails

class GetMatchDetailsUseCase(
    private val matchRepository: MatchRepository,
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, matchId: String): MatchDetails = transactionManager.runInTransaction {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")

        // 1. Single permission check using metadata from match
        checkUserPermissionUseCase(
            userId,
            match.clubId,
            minClubRole = ClubRole.MEMBER,
            minTeamRole = TeamRole.STAFF,
            teamId = match.teamId,
            allowPlayerOrParentAccess = true
        )

        val opponent = opponentRepository.getOpponentById(match.opponentId) ?: throw NotFoundException("Opponent not found")
        val callUpsWithPlayer = matchRepository.getCallUpsForMatch(matchId)

        MatchDetails(match, opponent, callUpsWithPlayer)
    }
}
