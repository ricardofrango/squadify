package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.repository.UserRepository
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.Player
import com.ricardofrango.squadify.util.LogoImage
import kotlin.uuid.Uuid

// Photos of people: a player's photo and a user's own profile photo. The same rules as the logos
// (ClubLogoUseCases): cut to a square PNG first (a bad file changes nothing), the old file deleted
// before the new one is stored under a new id, the URL cleared if storing fails, and only the
// current id served. See TECHNICAL_DECISIONS.md §57.

/**
 * Stores [bytes] as [ownerId]'s photo in place of [currentUrl]; [setUrl] saves the new path (or null
 * when storing failed). Returns the new path.
 */
private suspend fun replacePhoto(
    imageStorage: ImageStorage,
    paths: ImagePaths,
    ownerId: String,
    currentUrl: String?,
    bytes: ByteArray,
    setUrl: suspend (String?) -> Unit
): String {
    if (!imageStorage.isAvailable) throw ValidationException("Image uploads aren't set up on this server yet")
    val png = LogoImage.toPhotoPng(bytes)
    currentUrl?.let { paths.keyOf(ownerId, it) }?.let { imageStorage.delete(it) }
    val imageId = Uuid.random().toString()
    try {
        imageStorage.put(paths.key(ownerId, imageId), png, "image/png")
    } catch (e: Exception) {
        setUrl(null)
        throw e
    }
    return paths.url(ownerId, imageId).also { setUrl(it) }
}

/**
 * The player, if [userId] may change their photo: the same gate as editing the player
 * (`EDIT_PLAYER`, for the player's own age group's staff as far as the club allows), like
 * `PlayerProfile.canEdit`.
 */
private suspend fun loadPlayerForEdit(
    playerRepository: PlayerRepository,
    checkUserPermissionUseCase: CheckUserPermissionUseCase,
    transactionManager: TransactionManager,
    userId: String,
    playerId: String
): Player = transactionManager.runInTransaction {
    val player = playerRepository.getPlayerById(playerId) ?: throw NotFoundException("Player not found")
    checkUserPermissionUseCase(
        userId = userId,
        clubId = player.clubId,
        minClubRole = ClubRole.COORDINATOR,
        configurablePermission = ConfigurablePermission.EDIT_PLAYER,
        ageGroupId = playerRepository.getAgeGroupIdsByPlayer(playerId).firstOrNull(),
        errorMessage = "Insufficient permissions to change this player's photo"
    )
    player
}

/** Uploads or replaces a player's photo; returns the new `photo_url`. */
class UploadPlayerPhotoUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, playerId: String, bytes: ByteArray): String {
        val player = loadPlayerForEdit(playerRepository, checkUserPermissionUseCase, transactionManager, userId, playerId)
        return replacePhoto(imageStorage, PlayerPhotos, playerId, player.photoUrl, bytes) { url ->
            transactionManager.runInTransaction { playerRepository.updatePhotoUrl(playerId, url) }
        }
    }
}

/** Removes a player's photo: the file goes, and the player shows their initials again. */
class DeletePlayerPhotoUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, playerId: String) {
        val player = loadPlayerForEdit(playerRepository, checkUserPermissionUseCase, transactionManager, userId, playerId)
        val photoUrl = player.photoUrl ?: return
        PlayerPhotos.keyOf(playerId, photoUrl)?.let { imageStorage.delete(it) }
        transactionManager.runInTransaction { playerRepository.updatePhotoUrl(playerId, null) }
    }
}

/**
 * The photo's bytes, for the club's members: the same as who can open the player's profile (a
 * player is usually a minor, so not every family in the club). Only the current image id is served.
 */
class GetPlayerPhotoUseCase(
    private val playerRepository: PlayerRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, playerId: String, imageId: String): ByteArray {
        val player = transactionManager.runInTransaction {
            val player = playerRepository.getPlayerById(playerId) ?: throw NotFoundException("Photo not found")
            checkUserPermissionUseCase(userId, player.clubId, minClubRole = ClubRole.MEMBER)
            player
        }
        if (player.photoUrl != PlayerPhotos.url(playerId, imageId)) throw NotFoundException("Photo not found")
        return imageStorage.get(PlayerPhotos.key(playerId, imageId)) ?: throw NotFoundException("Photo not found")
    }
}

/** Uploads or replaces the caller's own profile photo; returns the new `avatar_url`. */
class UploadUserAvatarUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, bytes: ByteArray): String {
        val user = transactionManager.runInTransaction { userRepository.findById(userId) } ?: throw NotFoundException("User not found")
        return replacePhoto(imageStorage, UserAvatars, userId, user.avatarUrl, bytes) { url ->
            transactionManager.runInTransaction { userRepository.updateAvatarUrl(userId, url) }
        }
    }
}

/** Removes the caller's own profile photo; they show their initials again. */
class DeleteUserAvatarUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String) {
        val user = transactionManager.runInTransaction { userRepository.findById(userId) } ?: throw NotFoundException("User not found")
        val avatarUrl = user.avatarUrl ?: return
        UserAvatars.keyOf(userId, avatarUrl)?.let { imageStorage.delete(it) }
        transactionManager.runInTransaction { userRepository.updateAvatarUrl(userId, null) }
    }
}

/**
 * A user's profile photo, for any signed-in user. Adults' photos, shown wherever their name is (staff,
 * members, the More panel), so not gated per club: the path holds a random id that's only handed out
 * with the user's details, and only the current id is served, so a replaced photo's link stops working.
 */
class GetUserAvatarUseCase(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(ownerId: String, imageId: String): ByteArray {
        val user = transactionManager.runInTransaction { userRepository.findById(ownerId) } ?: throw NotFoundException("Photo not found")
        if (user.avatarUrl != UserAvatars.url(ownerId, imageId)) throw NotFoundException("Photo not found")
        return imageStorage.get(UserAvatars.key(ownerId, imageId)) ?: throw NotFoundException("Photo not found")
    }
}
