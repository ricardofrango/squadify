package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.scope
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.TeamRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.UpdateTeamRequest

class UpdateTeamUseCase(
    private val teamRepository: TeamRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val clubRepository: ClubRepository
) {
    suspend operator fun invoke(
        userId: String,
        teamId: String,
        request: UpdateTeamRequest
    ): Boolean = transactionManager.runInTransaction {
        val existingTeam = teamRepository.getTeamById(teamId)
            ?: throw NotFoundException("Team not found")

        accessPolicy.requireEditTeam(userId, existingTeam.scope, "Only admins, coordinators, or this team's coach can update it")

        if (request.name?.isBlank() == true) {
            throw ValidationException("Team name cannot be empty")
        }
        val teamSize = request.teamSize
        if (teamSize != null) {
            val club = clubRepository.getClubById(existingTeam.clubId) ?: throw NotFoundException("Club not found")
            if (teamSize !in club.sport.teamSizes) {
                throw ValidationException("Team size $teamSize is not valid for ${club.sport}")
            }
        }

        val updatedTeam = existingTeam.copy(
            name = request.name?.trim() ?: existingTeam.name,
            code = request.code ?: existingTeam.code,
            active = request.active ?: existingTeam.active,
            teamSize = teamSize ?: existingTeam.teamSize
        )

        teamRepository.updateTeam(updatedTeam)
    }
}
