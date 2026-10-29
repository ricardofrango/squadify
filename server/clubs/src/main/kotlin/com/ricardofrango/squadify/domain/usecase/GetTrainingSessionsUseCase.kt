package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.allowed
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TrainingRatingRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.AgeGroupRole
import com.ricardofrango.squadify.model.TrainingRating
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessions
import com.ricardofrango.squadify.model.attendance

/**
 * The Training tab's sessions (design 39a): every session of the age group, with how many exercises
 * each has. Players and parents of the age group see sessions too; the ratings fields are left out for
 * anyone who can't rate (they're staff-only).
 */
class GetTrainingSessionsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val clubRepository: ClubRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    private val trainingRatingRepository: TrainingRatingRepository,
    private val userRepository: UserRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, ageGroupId: String): TrainingSessions = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
        checkUserPermissionUseCase(
            userId, ageGroup.clubId,
            minClubRole = ClubRole.MEMBER,
            minAgeGroupRole = AgeGroupRole.STAFF,
            ageGroupId = ageGroupId,
            allowPlayerOrParentAccess = true
        )
        val club = clubRepository.getClubById(ageGroup.clubId) ?: throw NotFoundException("Club not found")
        val season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: throw NotFoundException("Season not found")
        val canEdit = allowed { checkUserPermissionUseCase.requireManageTraining(userId, ageGroup) }
        val canRate = allowed { checkUserPermissionUseCase.requireRateTraining(userId, ageGroup) }

        val stored = trainingSessionRepository.getByAgeGroup(ageGroupId)
        val counts = trainingSessionRepository.contentCounts(stored.map { it.session.id })
        val ratings = if (canRate) trainingRatingRepository.getForSessions(stored.map { it.session.id }) else emptyMap()
        val cancellers = stored.mapNotNull { it.cancelledBy }.distinct()
            .associateWith { id -> userRepository.findById(id)?.let { "${it.firstName} ${it.lastName}" } }

        TrainingSessions(
            ageGroup = ageGroup,
            clubName = club.name,
            season = season,
            stadiumLocation = club.stadiumLocation,
            country = club.country,
            sessions = stored.map { s ->
                s.session.copy(
                    cancelledByName = s.cancelledBy?.let { cancellers[it] },
                    exerciseCount = counts[s.session.id]?.exercises ?: 0
                ).withRatings(canRate, ratings[s.session.id].orEmpty())
            },
            canEdit = canEdit,
            canRate = canRate
        )
    }
}

/** For those who rate: how many players are rated and the average of those who trained (Absent excluded). */
internal fun TrainingSession.withRatings(canRate: Boolean, ratings: List<TrainingRating>): TrainingSession =
    if (!canRate) copy(ratedCount = null, averageRating = null)
    else ratings.map { it.rating }.attendance().let { copy(ratedCount = it.rated, averageRating = it.average) }

