package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ConflictException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.CreateTrainingSessionsRequest
import com.ricardofrango.squadify.model.MAX_BULK_SESSIONS
import com.ricardofrango.squadify.model.ReplacedSession
import com.ricardofrango.squadify.model.TrainingSession
import com.ricardofrango.squadify.model.TrainingSessionsPreview
import com.ricardofrango.squadify.model.plannedSessions
import com.ricardofrango.squadify.model.publicHolidaysBetween
import kotlin.uuid.Uuid

/**
 * Creating an age group's sessions in bulk (design 39c/d), behind `MANAGE_TRAINING`:
 * - Every date in the period on a slot's weekdays gets a session. The period isn't bound by the season's
 *   dates: those are the football association's calendar, and clubs often train past its end (or before it).
 * - The club country's national holidays are skipped unless [CreateTrainingSessionsRequest.includeHolidays].
 * - A session already at the same date and start time is replaced (deleted with its exercises and ratings).
 * [preview] shows all that without changing anything; [create] does it, and refuses (409) to replace a
 * session with content unless [CreateTrainingSessionsRequest.confirmReplacingContent].
 */
class CreateTrainingSessionsUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val clubRepository: ClubRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend fun preview(userId: String, ageGroupId: String, request: CreateTrainingSessionsRequest): TrainingSessionsPreview =
        transactionManager.runInTransaction { buildPreview(userId, ageGroupId, request) }

    /** Returns how many sessions were created. */
    suspend fun create(userId: String, ageGroupId: String, request: CreateTrainingSessionsRequest): Int = transactionManager.runInTransaction {
        val preview = buildPreview(userId, ageGroupId, request)
        if (preview.replacingWithContent > 0 && !request.confirmReplacingContent) {
            throw ConflictException("${preview.replacingWithContent} sessions with exercises or ratings would be replaced")
        }
        val toCreate = preview.toCreate
        trainingSessionRepository.deleteAll(toCreate.mapNotNull { it.replaces?.sessionId })
        val location = request.location.cleaned()
        trainingSessionRepository.insertAll(toCreate.map {
            TrainingSession(Uuid.random().toString(), ageGroupId, it.date, it.startTime, it.endTime, location = location)
        })
        toCreate.size
    }

    private suspend fun buildPreview(userId: String, ageGroupId: String, request: CreateTrainingSessionsRequest): TrainingSessionsPreview {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
        checkUserPermissionUseCase.requireManageTraining(userId, ageGroup)
        val club = clubRepository.getClubById(ageGroup.clubId) ?: throw NotFoundException("Club not found")

        if (request.slots.isEmpty()) throw ValidationException("Add at least one training day")
        request.slots.forEach { slot ->
            if (slot.weekdays.isEmpty() || slot.weekdays.any { it !in 1..7 }) throw ValidationException("Every slot needs at least one weekday")
            if (slot.endTime <= slot.startTime) throw ValidationException("A slot must end after it starts")
        }
        if (request.endDate < request.startDate) throw ValidationException("The period can't end before it starts")

        val holidays = publicHolidaysBetween(club.country, request.startDate, request.endDate)
        val planned = plannedSessions(request.slots, request.startDate, request.endDate, holidays, request.includeHolidays)
        if (planned.count { it.skippedHoliday == null } > MAX_BULK_SESSIONS) {
            throw ValidationException("At most $MAX_BULK_SESSIONS sessions can be created at once")
        }

        val existing = trainingSessionRepository.getByAgeGroup(ageGroupId)
            .associateBy { it.session.date to it.session.startTime }
        val counts = trainingSessionRepository.contentCounts(existing.values.map { it.session.id })
        return TrainingSessionsPreview(planned.map { item ->
            if (item.skippedHoliday != null) return@map item
            val old = existing[item.date to item.startTime] ?: return@map item
            val c = counts[old.session.id]
            item.copy(replaces = ReplacedSession(old.session.id, c?.exercises ?: 0, c?.ratings ?: 0))
        })
    }
}
