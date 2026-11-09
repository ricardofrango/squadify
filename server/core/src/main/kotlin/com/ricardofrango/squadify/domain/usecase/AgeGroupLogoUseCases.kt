package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.access.GameScope
import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.AgeGroup
import com.ricardofrango.squadify.util.LogoImage
import kotlin.uuid.Uuid

// An age group's crest, the club it plays for (the coach app's "Povoense - Sub 11"), works like an
// opponent's logo (OpponentLogoUseCases): checked and resized to a 512×512 PNG, a new image id on every
// upload, the old file deleted first, and only the current one served. See TECHNICAL_DECISIONS.md §57.

/** The age group, if [userId] may change its crest: the same gate as editing it. */
private suspend fun loadAgeGroupForEdit(
    ageGroupRepository: AgeGroupRepository,
    accessPolicy: AccessPolicy,
    transactionManager: TransactionManager,
    userId: String,
    ageGroupId: String
): AgeGroup = transactionManager.runInTransaction {
    val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Age group not found")
    accessPolicy.requireEditAgeGroup(
        userId,
        GameScope(ageGroup.clubId, ageGroupId = ageGroupId),
        "Only admins, coordinators, or this age group's coaches can change its crest"
    )
    ageGroup
}

/**
 * Uploads or replaces an age group's crest; returns the new `logo_url`. A bad file leaves the current
 * crest alone; if storing the new one fails, the age group is left with none rather than a broken one.
 */
class UploadAgeGroupLogoUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, ageGroupId: String, bytes: ByteArray): String {
        val ageGroup = loadAgeGroupForEdit(ageGroupRepository, accessPolicy, transactionManager, userId, ageGroupId)
        if (!imageStorage.isAvailable) throw ValidationException("Image uploads aren't set up on this server yet")
        val png = LogoImage.toPng(bytes)

        ageGroup.logoUrl?.let { AgeGroupLogos.keyOf(ageGroupId, it) }?.let { imageStorage.delete(it) }

        val imageId = Uuid.random().toString()
        try {
            imageStorage.put(AgeGroupLogos.key(ageGroupId, imageId), png, "image/png")
        } catch (e: Exception) {
            transactionManager.runInTransaction { ageGroupRepository.updateLogoUrl(ageGroupId, null) }
            throw e
        }
        val url = AgeGroupLogos.url(ageGroupId, imageId)
        transactionManager.runInTransaction { ageGroupRepository.updateLogoUrl(ageGroupId, url) }
        return url
    }
}

/** Removes an age group's crest: the file goes, and the age group shows its initials again. */
class DeleteAgeGroupLogoUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, ageGroupId: String) {
        val ageGroup = loadAgeGroupForEdit(ageGroupRepository, accessPolicy, transactionManager, userId, ageGroupId)
        val logoUrl = ageGroup.logoUrl ?: return
        AgeGroupLogos.keyOf(ageGroupId, logoUrl)?.let { imageStorage.delete(it) }
        transactionManager.runInTransaction { ageGroupRepository.updateLogoUrl(ageGroupId, null) }
    }
}

/**
 * The crest's bytes, for anyone who can see the age group's club (or space). Only the current image id
 * is served, so an old link stops working.
 */
class GetAgeGroupLogoUseCase(
    private val ageGroupRepository: AgeGroupRepository,
    private val accessPolicy: AccessPolicy,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, ageGroupId: String, imageId: String): ByteArray {
        val ageGroup = transactionManager.runInTransaction {
            val ageGroup = ageGroupRepository.getAgeGroupById(ageGroupId) ?: throw NotFoundException("Logo not found")
            accessPolicy.requireViewClub(userId, ageGroup.clubId)
            ageGroup
        }
        if (ageGroup.logoUrl != AgeGroupLogos.url(ageGroupId, imageId)) throw NotFoundException("Logo not found")
        return imageStorage.get(AgeGroupLogos.key(ageGroupId, imageId)) ?: throw NotFoundException("Logo not found")
    }
}
