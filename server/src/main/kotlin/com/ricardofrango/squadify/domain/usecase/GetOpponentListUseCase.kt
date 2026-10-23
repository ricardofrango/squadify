package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.OpponentList
import com.ricardofrango.squadify.model.AgeGroupRole

/**
 * The Opponents screen (design 38): the club's opponents by name and whether the caller may create one.
 * Who may read it is the same as [GetOpponentsUseCase], including the [ageGroupId] path for a coach
 * picking an opponent for their own age group's match.
 */
class GetOpponentListUseCase(
    private val opponentRepository: OpponentRepository,
    private val clubRepository: ClubRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, clubId: String, ageGroupId: String? = null): OpponentList = transactionManager.runInTransaction {
        ageGroupId?.let { id ->
            val ageGroup = ageGroupRepository.getAgeGroupById(id)
                ?: throw NotFoundException("Age group not found")
            if (ageGroup.clubId != clubId) {
                throw ForbiddenException("Age group does not belong to this club")
            }
        }

        checkUserPermissionUseCase(
            userId, clubId,
            minClubRole = ClubRole.MEMBER,
            minAgeGroupRole = AgeGroupRole.COACH,
            ageGroupId = ageGroupId
        )

        val club = clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")
        val opponents = opponentRepository.getOpponentsByClub(clubId).sortedBy { it.name.lowercase() }

        val canCreate = try {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.COORDINATOR,
                configurablePermission = ConfigurablePermission.CREATE_OPPONENT,
                ageGroupId = ageGroupId
            )
            true
        } catch (e: ForbiddenException) {
            false
        }

        OpponentList(
            clubName = club.name,
            opponents = opponents,
            canCreate = canCreate
        )
    }
}
