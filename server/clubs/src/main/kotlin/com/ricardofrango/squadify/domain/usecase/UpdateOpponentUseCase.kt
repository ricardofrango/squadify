package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.UpdateOpponentRequest

class UpdateOpponentUseCase(
    private val opponentRepository: OpponentRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
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

        checkUserPermissionUseCase(
            userId = userId,
            clubId = existingOpponent.clubId,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.EDIT_OPPONENT,
            ageGroupId = request.ageGroupId,
            errorMessage = "Only admins, coordinators, or this age group's coaches can update opponents"
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
