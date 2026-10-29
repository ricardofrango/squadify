package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.UpdateSeasonRequest

class UpdateSeasonUseCase(
    private val seasonRepository: SeasonRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        seasonId: String,
        request: UpdateSeasonRequest
    ): Boolean = transactionManager.runInTransaction {
        val existingSeason = seasonRepository.getSeasonById(seasonId)
            ?: throw NotFoundException("Season not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = existingSeason.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can update seasons"
        )

        val updatedSeason = existingSeason.copy(
            name = request.name ?: existingSeason.name,
            type = request.type ?: existingSeason.type,
            startDate = request.startDate ?: existingSeason.startDate,
            endDate = request.endDate ?: existingSeason.endDate,
            active = request.active ?: existingSeason.active
        )

        seasonRepository.updateSeason(updatedSeason)
    }
}
