package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchEventRepository
import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.LiveMatchUpdate
import com.ricardofrango.footballmanager.model.MatchStatus
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.TeamStaffRole
import com.ricardofrango.footballmanager.security.MatchSessionManager

class UpdateMatchStatusUseCase(
    private val matchRepository: MatchRepository,
    private val matchEventRepository: MatchEventRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, matchId: String, status: MatchStatus): Boolean {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")
        if (match.clubId != clubId) throw ForbiddenException("Match does not belong to this club")

        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        val isAdminOrCoordinator = userRole == Role.CLUB_ADMIN || userRole == Role.COORDINATOR
        val isCoachOfTeam = teamRepository.isUserStaffOfTeam(userId, match.teamId, TeamStaffRole.HEAD_COACH)
                || teamRepository.isUserStaffOfTeam(userId, match.teamId, TeamStaffRole.ASSISTANT_COACH)

        if (!isAdminOrCoordinator && !isCoachOfTeam) {
            throw ForbiddenException("Only admins, coordinators, or team coaches can update match status")
        }

        val success = matchRepository.updateMatchStatus(matchId, status)
        if (success) {
            val score = matchEventRepository.getScoreByMatch(matchId)
            MatchSessionManager.broadcastUpdate(matchId, LiveMatchUpdate(score = score, status = status))
        }
        return success
    }
}
