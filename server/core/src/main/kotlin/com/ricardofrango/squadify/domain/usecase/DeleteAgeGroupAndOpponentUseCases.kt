package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.MatchRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.service.ClubMatchesBus
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubMatchesUpdate

/**
 * Permanently deletes an age group (`DELETE /v1/age-groups/{id}`): its teams with every game (played ones
 * too), its training, staff, pool and crest, and on the coach server ([AccessPolicy.deletesLonePlayers])
 * its players in no other age group. Behind [AccessPolicy.requireDeleteAgeGroup]: a club's coordinators
 * and admins, a space's owner. The images go after the commit, and open screens hear which games went.
 * Returns how many games were deleted.
 */
class DeleteAgeGroupUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage,
    private val clubMatchesBus: ClubMatchesBus
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): Int {
        val (clubId, deleted) = transactionManager.runInTransaction {
            val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
            accessPolicy.requireDeleteAgeGroup(
                userId,
                GameScope(ageGroup.clubId, ageGroupId = ageGroupId),
                "Only admins or coordinators can delete age groups"
            )
            ageGroup.clubId to ageGroupRepository.deleteAgeGroup(ageGroupId, accessPolicy.deletesLonePlayers())
        }
        // After the commit: a file that can't be removed never undoes the delete.
        val images = listOfNotNull(deleted.crestUrl?.let { AgeGroupLogos.keyOf(ageGroupId, it) }) +
            deleted.deletedPlayers.mapNotNull { (playerId, photo) -> photo?.let { PlayerPhotos.keyOf(playerId, it) } }
        images.forEach { runCatching { imageStorage.delete(it) } }
        deleted.matchIds.forEach { clubMatchesBus.publish(clubId, ClubMatchesUpdate(deletedMatchId = it)) }
        return deleted.matchIds.size
    }
}

/**
 * Permanently deletes an opponent (`DELETE /v1/opponents/{id}`) with every game against it, played ones
 * too (each with its call-ups, events, line-ups, stats and report), and its logo. Behind
 * [AccessPolicy.requireDeleteOpponent]: a club's coordinators and admins, a space's owner. Returns how
 * many games were deleted.
 */
class DeleteOpponentUseCase(
    private val opponentRepository: OpponentRepository,
    private val matchRepository: MatchRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage,
    private val clubMatchesBus: ClubMatchesBus
) {
    suspend operator fun invoke(userId: String, opponentId: String): Int {
        val (opponent, matchIds) = transactionManager.runInTransaction {
            val opponent = opponentRepository.getOpponentById(opponentId) ?: throw NotFoundException("Opponent not found")
            accessPolicy.requireDeleteOpponent(userId, GameScope(opponent.clubId), "Only admins or coordinators can delete opponents")
            val matchIds = matchRepository.getMatchesByOpponent(opponentId).map { it.id }
            matchIds.forEach { matchRepository.deleteMatch(it) }
            opponentRepository.deleteOpponent(opponentId)
            opponent to matchIds
        }
        opponent.logoUrl?.let { OpponentLogos.keyOf(opponentId, it) }?.let { runCatching { imageStorage.delete(it) } }
        matchIds.forEach { clubMatchesBus.publish(opponent.clubId, ClubMatchesUpdate(deletedMatchId = it)) }
        return matchIds.size
    }
}
