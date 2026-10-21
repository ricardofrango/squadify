package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.util.LogoImage
import kotlin.uuid.Uuid

/**
 * Where a club's logo lives. Each upload gets a new image id, so the path changes with the logo and an
 * app can cache a logo forever: `clubs/{clubId}/logo/{imageId}.png` in storage, and the same path under
 * the API (`/clubs/{clubId}/logo/{imageId}.png`) in `clubs.logo_url`.
 */
object ClubLogos {
    fun key(clubId: String, imageId: String) = "clubs/$clubId/logo/$imageId.png"
    fun url(clubId: String, imageId: String) = "/${key(clubId, imageId)}"

    /** The storage key behind a club's `logo_url`, or null when it isn't one of ours. */
    fun keyOf(clubId: String, logoUrl: String): String? {
        val imageId = logoUrl.removePrefix("/clubs/$clubId/logo/").removeSuffix(".png")
        if (imageId == logoUrl || imageId.isEmpty()) return null
        return runCatching { Uuid.parse(imageId) }.getOrNull()?.let { key(clubId, imageId) }
    }
}

private suspend fun loadClubAsCoordinator(
    clubRepository: ClubRepository,
    checkUserPermissionUseCase: CheckUserPermissionUseCase,
    transactionManager: TransactionManager,
    userId: String,
    clubId: String
): Club = transactionManager.runInTransaction {
    val club = clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")
    // The same gate as editing the club's details.
    checkUserPermissionUseCase(
        userId = userId,
        clubId = clubId,
        minClubRole = ClubRole.COORDINATOR,
        errorMessage = "Only club admins or coordinators can change the club's logo"
    )
    club
}

/**
 * Uploads or replaces the club's logo. The image is checked and resized first, so a bad file leaves
 * the current logo alone. Then the old file is deleted before the new one is stored (no orphan files
 * piling up), under a new image id (no stale caches). If storing the new one fails, the club is left
 * with no logo rather than pointing at a deleted file.
 */
class UploadClubLogoUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    /** Returns the new `logo_url`. */
    suspend operator fun invoke(userId: String, clubId: String, bytes: ByteArray): String {
        val club = loadClubAsCoordinator(clubRepository, checkUserPermissionUseCase, transactionManager, userId, clubId)
        if (!imageStorage.isAvailable) throw ValidationException("Image uploads aren't set up on this server yet")
        val png = LogoImage.toPng(bytes)

        club.logoUrl?.let { ClubLogos.keyOf(clubId, it) }?.let { imageStorage.delete(it) }

        val imageId = Uuid.random().toString()
        try {
            imageStorage.put(ClubLogos.key(clubId, imageId), png, "image/png")
        } catch (e: Exception) {
            transactionManager.runInTransaction { clubRepository.updateLogoUrl(clubId, null) }
            throw e
        }
        val url = ClubLogos.url(clubId, imageId)
        transactionManager.runInTransaction { clubRepository.updateLogoUrl(clubId, url) }
        return url
    }
}

/** Removes the club's logo: the file goes, and the club shows its initials again. */
class DeleteClubLogoUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, clubId: String) {
        val club = loadClubAsCoordinator(clubRepository, checkUserPermissionUseCase, transactionManager, userId, clubId)
        val logoUrl = club.logoUrl ?: return
        ClubLogos.keyOf(clubId, logoUrl)?.let { imageStorage.delete(it) }
        transactionManager.runInTransaction { clubRepository.updateLogoUrl(clubId, null) }
    }
}

/**
 * The logo's bytes, for anyone who can see the club (members, and players' families, like the club's
 * details). Only the club's current image id is served, so an old link stops working once replaced.
 */
class GetClubLogoUseCase(
    private val clubRepository: ClubRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, clubId: String, imageId: String): ByteArray {
        val club = transactionManager.runInTransaction {
            checkUserPermissionUseCase(
                userId = userId,
                clubId = clubId,
                minClubRole = ClubRole.MEMBER,
                allowPlayerOrParentAccess = true,
                errorMessage = "You do not have access to this club"
            )
            clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")
        }
        if (club.logoUrl != ClubLogos.url(clubId, imageId)) throw NotFoundException("Logo not found")
        return imageStorage.get(ClubLogos.key(clubId, imageId)) ?: throw NotFoundException("Logo not found")
    }
}
