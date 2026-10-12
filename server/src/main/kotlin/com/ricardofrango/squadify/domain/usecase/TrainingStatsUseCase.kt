package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.AgeGroupTrainingStats
import com.ricardofrango.squadify.model.PlayerTrainingSeason
import com.ricardofrango.squadify.model.PlayerTrainingStats
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Training stats (design 39j), staff only like the ratings they come from. Only sessions that have ended
 * and weren't cancelled count. Attendance is the sessions a player was rated above 0 out of those; the
 * average leaves Absent (0) out.
 */
class TrainingStatsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val playerRepository: PlayerRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    private val trainingRatingRepository: TrainingRatingRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    suspend fun ageGroup(userId: String, ageGroupId: String): AgeGroupTrainingStats = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
        checkUserPermissionUseCase.requireRateTraining(userId, ageGroup)
        val sessions = endedSessions(ageGroupId)
        AgeGroupTrainingStats(
            players = playerRepository.getPlayersByAgeGroup(ageGroupId),
            sessions = sessions,
            ratings = trainingRatingRepository.getForSessions(sessions.map { it.id })
        )
    }

    suspend fun player(userId: String, playerId: String): PlayerTrainingStats = transactionManager.runInTransaction {
        playerRepository.getPlayerById(playerId) ?: throw NotFoundException("Player not found")
        val seasons = playerRepository.getAgeGroupIdsByPlayer(playerId).mapNotNull { id ->
            val ageGroup = ageGroupRepository.getAgeGroupById(id) ?: return@mapNotNull null
            if (!allowed { checkUserPermissionUseCase.requireRateTraining(userId, ageGroup) }) return@mapNotNull null
            val season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: return@mapNotNull null
            val sessions = endedSessions(id)
            val ratings = trainingRatingRepository.getForSessions(sessions.map { it.id })
                .values.flatten().filter { it.playerId == playerId }.map { it.rating }
            val trained = ratings.filter { it > 0.0 }
            PlayerTrainingSeason(
                seasonId = season.id, seasonName = season.name, ageGroupName = ageGroup.name,
                sessions = sessions.size, attended = trained.size, rated = ratings.size,
                average = trained.takeIf { it.isNotEmpty() }?.average()
            ) to season.endDate
        }
        PlayerTrainingStats(seasons.sortedByDescending { it.second }.map { it.first })
    }

    private suspend fun endedSessions(ageGroupId: String) =
        trainingSessionRepository.getByAgeGroup(ageGroupId).map { it.session }.filter { it.ratingsOpen(now()) }

    private fun now(): LocalDateTime = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
