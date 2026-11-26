package com.sportssoftw.squadcall.domain.usecase

import com.sportssoftw.squadcall.domain.repository.ClubRepository
import com.sportssoftw.squadcall.domain.service.TransactionManager
import com.sportssoftw.squadcall.exception.NotFoundException
import com.sportssoftw.squadcall.exception.ValidationException
import com.sportssoftw.squadcall.model.ClubRole
import com.sportssoftw.squadcall.model.ClubThemeProblem
import com.sportssoftw.squadcall.model.ClubThemeRules
import com.sportssoftw.squadcall.model.UpdateClubRequest

class UpdateClubUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(
        userId: String,
        clubId: String,
        request: UpdateClubRequest
    ): Boolean = transactionManager.runInTransaction {
        if (request.name?.isBlank() == true) {
            throw ValidationException("Club name cannot be blank")
        }
        if (request.clearTheme && request.theme != null) {
            throw ValidationException("Send either new club colours or clearTheme, not both")
        }
        val theme = request.theme?.let(ClubThemeRules::normalized)
        theme?.let(ClubThemeRules::problem)?.let { throw ValidationException(it.message()) }

        val existingClub = clubRepository.getClubById(clubId)
            ?: throw NotFoundException("Club not found")

        checkUserPermissionUseCase(
            userId = userId,
            clubId = clubId,
            minClubRole = ClubRole.COORDINATOR,
            errorMessage = "Only club admins or coordinators can update club details"
        )

        val updatedClub = existingClub.copy(
            name = request.name ?: existingClub.name,
            shortName = request.shortName ?: existingClub.shortName,
            // Only the logo upload sets it (UploadClubLogoUseCase), never a URL sent here.
            logoUrl = existingClub.logoUrl,
            email = request.email ?: existingClub.email,
            phone = request.phone ?: existingClub.phone,
            address = request.address ?: existingClub.address,
            stadiumLocation = request.stadiumLocation ?: existingClub.stadiumLocation,
            country = request.country ?: existingClub.country,
            status = request.status ?: existingClub.status,
            theme = if (request.clearTheme) null else theme ?: existingClub.theme
        )

        clubRepository.updateClub(updatedClub)
    }
}

private fun ClubThemeProblem.message() = when (this) {
    ClubThemeProblem.INVALID_COLOR -> "Club colours must be #RRGGBB"
    ClubThemeProblem.PRIMARY_TOO_LIGHT -> "The main colour is too light for white text"
    ClubThemeProblem.SECONDARY_TOO_CLOSE -> "The second colour must be clearly lighter than the main colour"
}
