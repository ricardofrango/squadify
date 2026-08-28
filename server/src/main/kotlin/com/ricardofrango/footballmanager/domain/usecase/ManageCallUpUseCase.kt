package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.*

class ManageCallUpUseCase(
    private val matchRepository: MatchRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository
) {
    suspend fun manageCallUps(userId: String, clubId: String, matchId: String, playerIds: List<String>): Boolean {
        val match = matchRepository.getMatchById(matchId) ?: throw NotFoundException("Match not found")
        checkPermission(userId, clubId, match.teamId)

        return matchRepository.manageCallUps(matchId, playerIds)
    }

    suspend fun updateCallUpStatus(userId: String, clubId: String, callUpId: String, status: CallUpStatus, notes: String?): Boolean {
        val callUp = matchRepository.getCallUpById(callUpId) ?: throw NotFoundException("Call-up not found")
        val match = matchRepository.getMatchById(callUp.matchId) ?: throw NotFoundException("Match not found")
        
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        val isAdminOrCoordinator = userRole == Role.CLUB_ADMIN || userRole == Role.COORDINATOR
        val isCoachOfTeam = teamRepository.isUserStaffOfTeam(userId, match.teamId, TeamStaffRole.HEAD_COACH)
                || teamRepository.isUserStaffOfTeam(userId, match.teamId, TeamStaffRole.ASSISTANT_COACH)

        if (!isAdminOrCoordinator && !isCoachOfTeam) {
            throw ForbiddenException("Insufficient permissions to update call-up")
        }

        return matchRepository.updateCallUp(callUpId, status, notes)
    }

    private suspend fun checkPermission(userId: String, clubId: String, teamId: String) {
        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        val isAdminOrCoordinator = userRole == Role.CLUB_ADMIN || userRole == Role.COORDINATOR
        val isCoachOfTeam = teamRepository.isUserStaffOfTeam(userId, teamId, TeamStaffRole.HEAD_COACH)
                || teamRepository.isUserStaffOfTeam(userId, teamId, TeamStaffRole.ASSISTANT_COACH)

        if (!isAdminOrCoordinator && !isCoachOfTeam) {
            throw ForbiddenException("Insufficient permissions")
        }
    }
}
