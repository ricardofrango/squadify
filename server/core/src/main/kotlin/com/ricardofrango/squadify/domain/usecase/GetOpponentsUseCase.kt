package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.Opponent

class GetOpponentsUseCase(
    private val opponentRepository: OpponentRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    // ageGroupId: set when called from an age-group/match context (create-match screen) -- lets a
    // coach of specifically THIS age group see the list (e.g. a coach with no club_members row at all
    // still needs this to pick/create an opponent for their match). See CreatePlayersUseCase's
    // identical pattern.
    suspend operator fun invoke(userId: String, clubId: String, ageGroupId: String? = null): List<Opponent> = transactionManager.runInTransaction {
        ageGroupId?.let { id ->
            val ageGroup = ageGroupRepository.getAgeGroupById(id)
                ?: throw NotFoundException("Age group not found")
            if (ageGroup.clubId != clubId) {
                throw ForbiddenException("Age group does not belong to this club")
            }
        }

        // 1. Check permissions first (Member of club, or a coach of this specific age group -- a
        // coach with no club_members row at all still needs to see the opponent list to create a match).
        accessPolicy.requireViewOpponents(userId, GameScope(clubId, ageGroupId = ageGroupId))

        opponentRepository.getOpponentsByClub(clubId)
    }
}
