package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.access.allowed
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.repository.TrainingRatingRepository
import com.sportssoftw.squadcall.domain.repository.TrainingSessionRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.model.AgeGroupTrainingStats
import com.sportssoftw.squadcall.model.PlayerTrainingSeason
import com.sportssoftw.squadcall.model.PlayerTrainingSessionLine
import com.sportssoftw.squadcall.model.PlayerTrainingStats
import com.sportssoftw.squadcall.model.TrainingSession
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
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    suspend fun ageGroup(userId: String, ageGroupId: String): AgeGroupTrainingStats = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
        accessPolicy.requireRateTraining(userId, ageGroup.scope, RATE_TRAINING_MESSAGE)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)
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
            if (!allowed {
                    accessPolicy.requireRateTraining(userId, ageGroup.scope)
                    accessPolicy.requireTrainingInPlan(ageGroup.clubId)
                }
            ) return@mapNotNull null
            val season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: return@mapNotNull null
            val sessions = endedSessions(id)
            val bySession = trainingRatingRepository.getForSessions(sessions.map { it.id })
                .mapValues { (_, ratings) -> ratings.firstOrNull { it.playerId == playerId }?.rating }
            val ratings = bySession.values.filterNotNull()
            val trained = ratings.filter { it > 0.0 }
            PlayerTrainingSeason(
                seasonId = season.id, seasonName = season.name, ageGroupName = ageGroup.name,
                sessions = sessions.size, attended = trained.size, rated = ratings.size,
                average = trained.takeIf { it.isNotEmpty() }?.average(),
                sessionLines = sessions.sortedWith(compareByDescending<TrainingSession> { it.date }.thenByDescending { it.startTime })
                    .map { PlayerTrainingSessionLine(it.id, it.date, it.startTime, bySession[it.id]) }
            ) to season.endDate
        }
        PlayerTrainingStats(seasons.sortedByDescending { it.second }.map { it.first })
    }

    private suspend fun endedSessions(ageGroupId: String) =
        trainingSessionRepository.getByAgeGroup(ageGroupId).map { it.session }.filter { it.ratingsOpen(now()) }

    private fun now(): LocalDateTime = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
