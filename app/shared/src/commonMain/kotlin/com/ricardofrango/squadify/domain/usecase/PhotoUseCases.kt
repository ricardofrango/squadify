package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.UserRepository

/** Uploads or replaces the signed-in user's own photo (the server crops it square); returns its path. */
class UploadUserAvatarUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(bytes: ByteArray): String = userRepository.uploadAvatar(bytes)
}

/** Removes the signed-in user's own photo; their initials show again. */
class DeleteUserAvatarUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke() = userRepository.deleteAvatar()
}

/** Uploads or replaces a player's photo (the server crops it square); returns its path. */
class UploadPlayerPhotoUseCase(private val playerRepository: PlayerRepository) {
    suspend operator fun invoke(playerId: String, bytes: ByteArray): String = playerRepository.uploadPlayerPhoto(playerId, bytes)
}

/** Removes a player's photo; their initials show again. */
class DeletePlayerPhotoUseCase(private val playerRepository: PlayerRepository) {
    suspend operator fun invoke(playerId: String) = playerRepository.deletePlayerPhoto(playerId)
}
