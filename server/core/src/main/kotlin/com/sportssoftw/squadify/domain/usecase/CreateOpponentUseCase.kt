package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.access.AccessPolicy
import com.sportssoftw.squadify.domain.access.GameScope
import com.sportssoftw.squadify.domain.repository.AgeGroupRepository
import com.sportssoftw.squadify.domain.repository.OpponentRepository
import com.sportssoftw.squadify.domain.service.TransactionManager
import com.sportssoftw.squadify.exception.ForbiddenException
import com.sportssoftw.squadify.exception.NotFoundException
import com.sportssoftw.squadify.model.CreateOpponentRequest
import com.sportssoftw.squadify.model.Opponent
import kotlin.uuid.Uuid

class CreateOpponentUseCase(
    private val opponentRepository: OpponentRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        request: CreateOpponentRequest
    ): Opponent = transactionManager.runInTransaction {
        request.ageGroupId?.let { id ->
            val ageGroup = ageGroupRepository.getAgeGroupById(id)
                ?: throw NotFoundException("Age group not found")
            if (ageGroup.clubId != clubId) {
                throw ForbiddenException("Age group does not belong to this club")
            }
        }

        accessPolicy.requireCreateOpponent(
            userId,
            GameScope(clubId, ageGroupId = request.ageGroupId),
            "Only admins, coordinators, or this age group's coaches can create opponents"
        )

        val name = requireOpponentName(request.name)
        requireUniqueOpponentName(name, opponentRepository.getOpponentsByClub(clubId))

        val opponent = Opponent(
            id = Uuid.random().toString(),
            clubId = clubId,
            name = name,
            shortName = normalizeShortName(request.shortName),
            // Only the logo upload sets it (UploadOpponentLogoUseCase), never a URL sent here.
            logoUrl = null,
            stadiumName = request.stadiumName.cleaned(),
            stadiumAddress = request.stadiumAddress.cleaned(),
            contactEmail = request.contactEmail.cleaned(),
            contactPhone = request.contactPhone.cleaned(),
            notes = request.notes.cleaned()
        )

        opponentRepository.createOpponent(opponent)
    }
}
