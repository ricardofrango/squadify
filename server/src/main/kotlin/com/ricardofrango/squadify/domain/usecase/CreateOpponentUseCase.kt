package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.CreateOpponentRequest
import com.ricardofrango.squadify.model.Opponent
import kotlin.uuid.Uuid

class CreateOpponentUseCase(
    private val opponentRepository: OpponentRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
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

        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            configurablePermission = ConfigurablePermission.CREATE_OPPONENT,
            ageGroupId = request.ageGroupId,
            errorMessage = "Only admins, coordinators, or this age group's coaches can create opponents"
        )

        val opponent = Opponent(
            id = Uuid.random().toString(),
            clubId = clubId,
            name = request.name,
            shortName = request.shortName,
            logoUrl = request.logoUrl
        )

        opponentRepository.createOpponent(opponent)
    }
}
