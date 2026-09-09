package com.ricardofrango.clubmanager.domain.usecase

import com.ricardofrango.clubmanager.domain.repository.SeasonRepository
import com.ricardofrango.clubmanager.domain.service.TransactionManager
import com.ricardofrango.clubmanager.exception.ConflictException
import com.ricardofrango.clubmanager.exception.ValidationException
import com.ricardofrango.clubmanager.model.ClubRole
import com.ricardofrango.clubmanager.model.CreateSeasonRequest
import com.ricardofrango.clubmanager.model.Season
import com.ricardofrango.clubmanager.model.SeasonType
import kotlin.uuid.Uuid

class CreateSeasonUseCase(
    private val seasonRepository: SeasonRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: CreateSeasonRequest): Season = transactionManager.runInTransaction {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = request.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can create seasons"
        )

        // Client-side (the date pickers) already keeps this from happening in normal use, but the
        // client can't be trusted alone -- validate here too, and fail fast before the duplicate-
        // season query does any DB work for an already-invalid request.
        if (request.startDate > request.endDate) {
            throw ValidationException("Season start date must not be after the end date")
        }

        // Check for duplicate season
        val existing = seasonRepository.findByDetails(
            request.clubId,
            request.type,
            request.startDate,
            request.endDate
        )
        if (existing != null) {
            throw ConflictException("A season with these dates and type already exists for this club")
        }

        val inferredName = if (request.name.isNullOrBlank()) {
            when (request.type) {
                SeasonType.EUROPEAN_YEAR -> {
                    val start = request.startDate.year.toString().takeLast(2)
                    val end = request.endDate.year.toString().takeLast(2)
                    "$start/$end"
                }

                SeasonType.CALENDAR_YEAR -> request.startDate.year.toString()
            }
        } else {
            request.name
        }

        val season = Season(
            id = Uuid.random().toString(),
            clubId = request.clubId,
            name = inferredName!!,
            type = request.type,
            startDate = request.startDate,
            endDate = request.endDate,
            active = true
        )

        seasonRepository.createSeason(season)
    }
}
