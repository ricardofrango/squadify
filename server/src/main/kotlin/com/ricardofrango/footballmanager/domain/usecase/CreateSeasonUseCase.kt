package com.ricardofrango.footballmanager.domain.usecase

import com.ricardofrango.footballmanager.domain.repository.SeasonRepository
import com.ricardofrango.footballmanager.domain.repository.UserRepository
import com.ricardofrango.footballmanager.exception.ForbiddenException
import com.ricardofrango.footballmanager.model.CreateSeasonRequest
import com.ricardofrango.footballmanager.model.Role
import com.ricardofrango.footballmanager.model.Season
import com.ricardofrango.footballmanager.model.SeasonType
import kotlin.uuid.Uuid

class CreateSeasonUseCase(
    private val seasonRepository: SeasonRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(userId: String, request: CreateSeasonRequest): Boolean {
        val user = userRepository.findById(userId) ?: throw ForbiddenException("User not found")
        
        if (!user.isPlatformAdmin) {
            val userRole = userRepository.getUserRoleInClub(userId, request.clubId)
            if (userRole != Role.CLUB_ADMIN && userRole != Role.COORDINATOR) {
                throw ForbiddenException("Only admins or coordinators can create seasons")
            }
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
