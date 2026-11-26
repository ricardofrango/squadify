package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.SeasonRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.ConflictException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.CreateSeasonRequest
import com.sportssoftw.squadcall.model.Season
import com.sportssoftw.squadcall.model.SeasonType
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
