package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.exception.ConflictException
import com.ricardofrango.footballmanager.model.ClubRole
import com.ricardofrango.footballmanager.model.CreateSeasonRequest
import com.ricardofrango.footballmanager.model.Season
import com.ricardofrango.footballmanager.model.SeasonType
import kotlin.uuid.Uuid

class CreateSeasonUseCase(
    private val seasonRepository: SeasonRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase
) {
    suspend operator fun invoke(userId: String, request: CreateSeasonRequest): Season {
        checkUserPermissionUseCase(
            userId = userId,
            clubId = request.clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only admins or coordinators can create seasons"
        )

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

        return seasonRepository.createSeason(season)
    }
}
