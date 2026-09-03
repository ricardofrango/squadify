package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.service.TransactionManager
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.TeamRole
import com.ricardofrango.footballmanager.model.Match

class GetMatchesUseCase(
    private val matchRepository: MatchRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String, teamId: String? = null): List<Match> = transactionManager.runInTransaction {
        // 1. Check permissions first (Member of club)
        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.MEMBER,
            minTeamRole = TeamRole.STAFF,
            teamId = teamId
        )

        if (teamId != null) {
            matchRepository.getMatchesByTeam(teamId)
        } else {
            matchRepository.getMatchesByClub(clubId)
        }
    }
}
