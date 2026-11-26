package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.access.AccessPolicy
import com.sportssoftw.squadcall.domain.repository.AgeGroupRepository
import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.repository.PlayerRepository
import com.sportssoftw.squadcall.domain.repository.TrainingRatingRepository
import com.sportssoftw.squadcall.domain.repository.TrainingSessionRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.SaveTrainingRatingsRequest
import com.sportssoftw.squadcall.model.TrainingRatings
import com.sportssoftw.squadcall.model.TrainingSession
import com.sportssoftw.squadcall.model.ratingToHundredths
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Rating players after a training session (design 39g). Open to every coach and staff member of the age
 * group (and coordinators), never to players or parents. Ratings open when the session ends; each one is 0
 * ("did not take part") or 0.01–10.00 with up to two decimals, for players in the age group's pool. Saving is
 * partial, and a null clears a rating.
 */
class TrainingRatingsUseCase(
    private val trainingSessionRepository: TrainingSessionRepository,
    private val trainingRatingRepository: TrainingRatingRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val playerRepository: PlayerRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    suspend fun get(userId: String, sessionId: String): TrainingRatings = transactionManager.runInTransaction {
        val stored = trainingSessionRepository.getById(sessionId) ?: throw NotFoundException("Training session not found")
        val ageGroup = ageGroupRepository.getAgeGroupById(stored.session.ageGroupId) ?: throw NotFoundException("Age group not found")
        accessPolicy.requireRateTraining(userId, ageGroup.scope, RATE_TRAINING_MESSAGE)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)
        TrainingRatings(
            session = stored.session,
            ageGroupName = ageGroup.name,
            clubName = clubRepository.getClubById(ageGroup.clubId)?.name ?: throw NotFoundException("Club not found"),
            players = playerRepository.getPlayersByAgeGroup(ageGroup.id).sortedWith(compareBy(nullsLast()) { it.shirtNumber }),
            ratings = trainingRatingRepository.getForSession(sessionId),
            ratingsOpen = stored.session.ratingsOpen(now())
        )
    }

    suspend fun save(userId: String, sessionId: String, request: SaveTrainingRatingsRequest): TrainingRatings = transactionManager.runInTransaction {
        val stored = trainingSessionRepository.getById(sessionId) ?: throw NotFoundException("Training session not found")
        val ageGroup = ageGroupRepository.getAgeGroupById(stored.session.ageGroupId) ?: throw NotFoundException("Age group not found")
        accessPolicy.requireRateTraining(userId, ageGroup.scope, RATE_TRAINING_MESSAGE)
        accessPolicy.requireTrainingInPlan(ageGroup.clubId)
        if (!stored.session.ratingsOpen(now())) throw ValidationException("Ratings open when the session ends, and not for a cancelled one")
        val pool = playerRepository.getPlayersByAgeGroup(ageGroup.id).map { it.id }.toSet()
        val changes = request.ratings.associate { change ->
            if (change.playerId !in pool) throw ValidationException("Only players of the age group can be rated")
            change.playerId to change.rating?.let { ratingToHundredths(it) ?: throw ValidationException("A rating is 0 to 10, with up to two decimals") }
        }
        trainingRatingRepository.save(sessionId, changes, userId)
        get(userId, sessionId)
    }

    private fun now(): LocalDateTime = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
