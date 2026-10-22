package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository

/** Uploads or replaces the club's logo (the server resizes it); returns the new logo path. */
class UploadClubLogoUseCase(private val clubRepository: ClubRepository) {
    suspend operator fun invoke(clubId: String, bytes: ByteArray): String = clubRepository.uploadClubLogo(clubId, bytes)
}

/** Removes the club's logo; the club shows its initials again. */
class DeleteClubLogoUseCase(private val clubRepository: ClubRepository) {
    suspend operator fun invoke(clubId: String) = clubRepository.deleteClubLogo(clubId)
}
