package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AddPlayersToAgeGroupRequest
import com.ricardofrango.squadify.model.AgeGroupPlayer
import kotlin.uuid.Uuid

/**
 * Adds club players to an age-group pool. A club admin/coordinator, or a coach of this age group,
 * may do it. Every player must belong to the club and be **age-eligible** -- `birthDate.year >=
 * ageGroup.endYear` (the oldest birth year the group allows), i.e. the player is old enough for this
 * group or is playing up from a younger one; a player older than the group's range is rejected.
 * Players already in the group are silently skipped, so the call is idempotent.
 */
class AddPlayersToAgeGroupUseCase(
    private val playerRepository: PlayerRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        ageGroupId: String,
        request: AddPlayersToAgeGroupRequest
    ): List<AgeGroupPlayer> = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId)
            ?: throw NotFoundException("Age group not found")

        accessPolicy.requireManagePool(
            userId,
            GameScope(ageGroup.clubId, ageGroupId = ageGroupId),
            "Only admins, coordinators, or this age group's coaches can add players"
        )

        if (request.playerIds.isEmpty()) {
            throw ValidationException("At least one player must be provided")
        }

        val playersById = playerRepository.getPlayersData(request.playerIds).associateBy { it.id }
        val alreadyIn = playerRepository.getPlayersByAgeGroup(ageGroupId).map { it.id }.toSet()

        val toLink = request.playerIds.distinct().mapNotNull { playerId ->
            if (playerId in alreadyIn) return@mapNotNull null

            val player = playersById[playerId]
                ?: throw NotFoundException("Player $playerId not found")
            if (player.clubId != ageGroup.clubId) {
                throw ForbiddenException("Player $playerId does not belong to this club")
            }
            // A disabled player was removed from the club (DisablePlayerUseCase).
            if (!player.active) {
                throw ValidationException("Player ${player.firstName} ${player.lastName} is disabled")
            }
            if (player.birthDate.year < ageGroup.endYear) {
                throw ValidationException(
                    "Player ${player.firstName} ${player.lastName} is too old for this age group"
                )
            }

            AgeGroupPlayer(
                id = Uuid.random().toString(),
                ageGroupId = ageGroupId,
                playerId = playerId
            )
        }

        if (toLink.isEmpty()) emptyList() else playerRepository.addPlayersToAgeGroup(toLink)
    }
}
