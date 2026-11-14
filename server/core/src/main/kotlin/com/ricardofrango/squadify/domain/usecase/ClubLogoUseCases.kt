package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.repository.ClubRepository
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.Club
import com.ricardofrango.squadify.util.LogoImage
import kotlin.uuid.Uuid

// A coach's space is a club on the coach server, so its logo is this one (`/clubs/{spaceId}/logo/…`).

/** The club, if [userId] may change its details: coordinators and admins, or a space's owner. */
private suspend fun loadClubForEdit(
    clubRepository: ClubRepository,
    accessPolicy: AccessPolicy,
    transactionManager: TransactionManager,
    userId: String,
    clubId: String
): Club = transactionManager.runInTransaction {
    val club = clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")
    accessPolicy.requireUploadClubLogo(userId, clubId, "Only club admins or coordinators can change the club's logo")
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
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    /** Returns the new `logo_url`. */
    suspend operator fun invoke(userId: String, clubId: String, bytes: ByteArray): String {
        val club = loadClubForEdit(clubRepository, accessPolicy, transactionManager, userId, clubId)
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
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, clubId: String) {
        val club = loadClubForEdit(clubRepository, accessPolicy, transactionManager, userId, clubId)
        val logoUrl = club.logoUrl ?: return
        ClubLogos.keyOf(clubId, logoUrl)?.let { imageStorage.delete(it) }
        transactionManager.runInTransaction { clubRepository.updateLogoUrl(clubId, null) }
    }
}

/**
 * The logo's bytes, for anyone who can see the club (members, and players' families, like the club's
 * details; a space's owner and staff). Only the club's current image id is served, so an old link stops working once replaced.
 */
class GetClubLogoUseCase(
    private val clubRepository: ClubRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, clubId: String, imageId: String): ByteArray {
        val club = transactionManager.runInTransaction {
            accessPolicy.requireViewClub(userId, clubId)
            clubRepository.getClubById(clubId) ?: throw NotFoundException("Club not found")
        }
        if (club.logoUrl != ClubLogos.url(clubId, imageId)) throw NotFoundException("Logo not found")
        return imageStorage.get(ClubLogos.key(clubId, imageId)) ?: throw NotFoundException("Logo not found")
    }
}

/**
 * The club's current logo for a push notification: the phone downloads a notification's image without
 * signing in, so this is served to anyone -- but only the current random image id, which is only
 * handed out with the club's details and in its own pushes (`/v1/public/clubs/{clubId}/logo/{imageId}.png`).
 */
class GetPublicClubLogoUseCase(
    private val clubRepository: ClubRepository,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(clubId: String, imageId: String): ByteArray {
        val club = transactionManager.runInTransaction { clubRepository.getClubById(clubId) } ?: throw NotFoundException("Logo not found")
        if (club.logoUrl != ClubLogos.url(clubId, imageId)) throw NotFoundException("Logo not found")
        return imageStorage.get(ClubLogos.key(clubId, imageId)) ?: throw NotFoundException("Logo not found")
    }

    companion object {
        /** The public address of a club's [logoUrl] on this server at [publicBaseUrl]; null when either is missing. */
        fun publicUrl(publicBaseUrl: String?, clubId: String, logoUrl: String?): String? {
            if (publicBaseUrl == null || logoUrl == null || ClubLogos.keyOf(clubId, logoUrl) == null) return null
            return "$publicBaseUrl/v1/public$logoUrl"
        }
    }
}
