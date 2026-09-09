package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.AgeGroupRepository
import com.ricardofrango.clubmanager.domain.repository.PlayerRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ValidationException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.Player
import com.ricardofrango.clubmanager.model.TeamRole

/**
 * The players a coordinator or an age-group coach may pick from when adding to this age group: every
 * club player old enough for it (`birthDate.year >= ageGroup.birthYear`) who isn't already in it.
 */
class GetEligiblePlayersForAgeGroupUseCase(
    private val playerRepository: PlayerRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): List<Player> = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
            ?: throw ValidationException("Age group not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = ageGroup.clubId,
            minClubRole = ClubRole.MEMBER,
            minTeamRole = TeamRole.STAFF,
            ageGroupId = ageGroupId
        )

        val alreadyIn = playerRepository.getPlayersByAgeGroup(ageGroupId).map { it.id }.toSet()
        playerRepository.getPlayersByClub(ageGroup.clubId)
            .filter { it.birthDate.year >= ageGroup.birthYear && it.id !in alreadyIn }
    }
}
