package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.MatchRepository
import com.ricardofrango.footballmanager.domain.repository.OpponentRepository
import com.ricardofrango.footballmanager.domain.repository.TeamRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.exception.NotFoundException
import com.ricardofrango.footballmanager.model.*
import kotlin.uuid.Uuid

class CreateMatchUseCase(
    private val matchRepository: MatchRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val opponentRepository: OpponentRepository
) {
    suspend operator fun invoke(userId: String, clubId: String, request: CreateMatchRequest): Match {
        val team = teamRepository.getTeamById(request.teamId) ?: throw NotFoundException("Team not found")
        if (team.clubId != clubId) throw ForbiddenException("Team does not belong to this club")

        val opponent = opponentRepository.getOpponentById(request.opponentId) ?: throw NotFoundException("Opponent not found")
        if (opponent.clubId != clubId) throw ForbiddenException("Opponent does not belong to this club")

        val userRole = userRepository.getUserRoleInClub(userId, clubId)
        val isAdminOrCoordinator = userRole == Role.CLUB_ADMIN || userRole == Role.COORDINATOR
        val isCoachOfTeam = teamRepository.isUserStaffOfTeam(userId, request.teamId, TeamStaffRole.HEAD_COACH) 
                || teamRepository.isUserStaffOfTeam(userId, request.teamId, TeamStaffRole.ASSISTANT_COACH)

        if (!isAdminOrCoordinator && !isCoachOfTeam) {
            throw ForbiddenException("Only admins, coordinators, or team coaches can create matches")
        }

        val match = Match(
            id = Uuid.random().toString(),
            clubId = clubId,
            teamId = request.teamId,
            opponentId = request.opponentId,
            location = request.location,
            dateTime = request.dateTime,
            status = MatchStatus.SCHEDULED,
            notes = request.notes
        )

        return matchRepository.createMatch(match)
    }
}
