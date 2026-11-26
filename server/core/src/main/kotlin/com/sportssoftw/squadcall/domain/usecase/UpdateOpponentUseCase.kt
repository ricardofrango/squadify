package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.GameScope
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.OpponentRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ForbiddenException
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.UpdateOpponentRequest

class UpdateOpponentUseCase(
    private val opponentRepository: OpponentRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        opponentId: String,
        request: UpdateOpponentRequest
    ): Boolean = transactionManager.runInTransaction {
        val existingOpponent = opponentRepository.getOpponentById(opponentId)
            ?: throw NotFoundException("Opponent not found")

        request.ageGroupId?.let { ageGroupId ->
            val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
                ?: throw NotFoundException("Age group not found")
            if (ageGroup.clubId != existingOpponent.clubId) {
                throw ForbiddenException("Age group does not belong to this club")
            }
        }

        accessPolicy.requireEditOpponent(
            userId,
            GameScope(existingOpponent.clubId, ageGroupId = request.ageGroupId),
            "Only admins, coordinators, or this age group's coaches can update opponents"
        )

        val name = request.name?.let { requireOpponentName(it) } ?: existingOpponent.name
        if (!name.equals(existingOpponent.name, ignoreCase = true)) {
            requireUniqueOpponentName(name, opponentRepository.getOpponentsByClub(existingOpponent.clubId), exceptId = opponentId)
        }

        // null keeps a field; a blank value clears it (cleaned() turns it into null).
        val updatedOpponent = existingOpponent.copy(
            name = name,
            shortName = if (request.shortName != null) normalizeShortName(request.shortName) else existingOpponent.shortName,
            // Only the logo upload sets it (UploadOpponentLogoUseCase), never a URL sent here.
            logoUrl = existingOpponent.logoUrl,
            stadiumName = if (request.stadiumName != null) request.stadiumName.cleaned() else existingOpponent.stadiumName,
            stadiumAddress = if (request.stadiumAddress != null) request.stadiumAddress.cleaned() else existingOpponent.stadiumAddress,
            contactEmail = if (request.contactEmail != null) request.contactEmail.cleaned() else existingOpponent.contactEmail,
            contactPhone = if (request.contactPhone != null) request.contactPhone.cleaned() else existingOpponent.contactPhone,
            notes = if (request.notes != null) request.notes.cleaned() else existingOpponent.notes
        )

        opponentRepository.updateOpponent(updatedOpponent)
    }
}
