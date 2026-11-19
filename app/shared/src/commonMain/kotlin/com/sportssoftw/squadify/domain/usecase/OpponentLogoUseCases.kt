package com.sportssoftw.squadify.domain.usecase

import com.sportssoftw.squadify.domain.repository.OpponentRepository

/** Uploads or replaces an opponent's logo (the server resizes it); returns the new logo path. */
class UploadOpponentLogoUseCase(private val opponentRepository: OpponentRepository) {
    suspend operator fun invoke(opponentId: String, ageGroupId: String?, bytes: ByteArray): String =
        opponentRepository.uploadOpponentLogo(opponentId, ageGroupId, bytes)
}

/** Removes an opponent's logo; it shows its initials again. */
class DeleteOpponentLogoUseCase(private val opponentRepository: OpponentRepository) {
    suspend operator fun invoke(opponentId: String, ageGroupId: String?) =
        opponentRepository.deleteOpponentLogo(opponentId, ageGroupId)
}
