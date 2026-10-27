package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.SaveTrainingRatingsRequest
import com.ricardofrango.squadify.model.TrainingRatings
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.ratingToHundredths
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Whether a session's ratings are open: it has ended (in the server's time zone, the club's) and isn't cancelled. */
internal fun TrainingSession.ratingsOpen(now: LocalDateTime): Boolean =
    !cancelled && (date < now.date || (date == now.date && endTime <= now.time))

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
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val clock: Clock = Clock.System
) {
    suspend fun get(userId: String, sessionId: String): TrainingRatings = transactionManager.runInTransaction {
        val stored = trainingSessionRepository.getById(sessionId) ?: throw NotFoundException("Training session not found")
        val ageGroup = ageGroupRepository.getAgeGroupById(stored.session.ageGroupId) ?: throw NotFoundException("Age group not found")
        checkUserPermissionUseCase.requireRateTraining(userId, ageGroup)
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
        checkUserPermissionUseCase.requireRateTraining(userId, ageGroup)
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
