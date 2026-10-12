package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.SeasonRepository
import com.ricardofrango.squadify.domain.repository.TrainingSessionRepository
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.model.SaveTrainingSessionRequest
import com.ricardofrango.squadify.model.TrainingSession
import kotlin.uuid.Uuid

/**
 * Add one session, or edit one (design 39e), behind `MANAGE_TRAINING`. The date must fall inside the age
 * group's season and the end after the start. Adding (or moving) a session onto the date and start time of
 * another replaces that one, as bulk creation does.
 */
class SaveTrainingSessionUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val seasonRepository: SeasonRepository,
    private val trainingSessionRepository: TrainingSessionRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend fun add(userId: String, ageGroupId: String, request: SaveTrainingSessionRequest): TrainingSession = transactionManager.runInTransaction {
        val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
        checkUserPermissionUseCase.requireManageTraining(userId, ageGroup)
        validate(ageGroup, request)
        replaceClash(ageGroupId, request, keep = null)
        val session = TrainingSession(
            Uuid.random().toString(), ageGroupId, request.date, request.startTime, request.endTime,
            location = request.location.cleaned(), notes = request.notes.cleaned()
        )
        trainingSessionRepository.insertAll(listOf(session))
        session
    }

    suspend fun update(userId: String, sessionId: String, request: SaveTrainingSessionRequest): TrainingSession = transactionManager.runInTransaction {
        val stored = trainingSessionRepository.getById(sessionId) ?: throw NotFoundException("Training session not found")
        val ageGroup = ageGroupRepository.getAgeGroupById(stored.session.ageGroupId) ?: throw NotFoundException("Age group not found")
        checkUserPermissionUseCase.requireManageTraining(userId, ageGroup)
        validate(ageGroup, request)
        replaceClash(ageGroup.id, request, keep = sessionId)
        val updated = stored.session.copy(
            date = request.date, startTime = request.startTime, endTime = request.endTime,
            location = request.location.cleaned(), notes = request.notes.cleaned()
        )
        trainingSessionRepository.update(updated)
        updated
    }

    private suspend fun validate(ageGroup: AgeGroup, request: SaveTrainingSessionRequest) {
        val season = seasonRepository.getSeasonById(ageGroup.seasonId) ?: throw NotFoundException("Season not found")
        if (request.endTime <= request.startTime) throw ValidationException("A session must end after it starts")
        if (request.date < season.startDate || request.date > season.endDate) {
            throw ValidationException("The date must fall inside the season (${season.startDate} – ${season.endDate})")
        }
    }

    private suspend fun replaceClash(ageGroupId: String, request: SaveTrainingSessionRequest, keep: String?) {
        val clash = trainingSessionRepository.getByAgeGroup(ageGroupId).filter {
            it.session.id != keep && it.session.date == request.date && it.session.startTime == request.startTime
        }
        trainingSessionRepository.deleteAll(clash.map { it.session.id })
    }
}
