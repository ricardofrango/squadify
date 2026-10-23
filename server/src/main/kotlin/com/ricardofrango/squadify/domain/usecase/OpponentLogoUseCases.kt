package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.AgeGroupRepository
import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.ForbiddenException
import com.ricardofrango.squadify.exception.NotFoundException
import com.ricardofrango.squadify.exception.ValidationException
import com.ricardofrango.squadify.model.ClubRole
import com.ricardofrango.squadify.model.ConfigurablePermission
import com.ricardofrango.squadify.model.Opponent
import com.ricardofrango.squadify.util.LogoImage
import kotlin.uuid.Uuid

// An opponent's logo works like the club's (ClubLogoUseCases): checked and resized to a 512×512 PNG,
// a new image id on every upload, the old file deleted first, and only the current one served.
// See TECHNICAL_DECISIONS.md §57.

/**
 * The opponent, if [userId] may change its logo: the same gate as editing the opponent
 * (`EDIT_OPPONENT`, with [ageGroupId] letting that age group's coaches in, as on Edit opponent).
 */
private suspend fun loadOpponentForEdit(
    opponentRepository: OpponentRepository,
    ageGroupRepository: AgeGroupRepository,
    checkUserPermissionUseCase: CheckUserPermissionUseCase,
    transactionManager: TransactionManager,
    userId: String,
    opponentId: String,
    ageGroupId: String?
): Opponent = transactionManager.runInTransaction {
    val opponent = opponentRepository.getOpponentById(opponentId) ?: throw NotFoundException("Opponent not found")
    ageGroupId?.let { id ->
        val ageGroup = ageGroupRepository.getAgeGroupById(id) ?: throw NotFoundException("Age group not found")
        if (ageGroup.clubId != opponent.clubId) throw ForbiddenException("Age group does not belong to this club")
    }
    checkUserPermissionUseCase(
        userId = userId,
        clubId = opponent.clubId,
        minClubRole = ClubRole.COORDINATOR,
        configurablePermission = ConfigurablePermission.EDIT_OPPONENT,
        ageGroupId = ageGroupId,
        errorMessage = "Only admins, coordinators, or this age group's coaches can change an opponent's logo"
    )
    opponent
}

/**
 * Uploads or replaces an opponent's logo; returns the new `logo_url`. A bad file leaves the current
 * logo alone; if storing the new one fails, the opponent is left with no logo rather than a broken one.
 */
class UploadOpponentLogoUseCase(
    private val opponentRepository: OpponentRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, opponentId: String, ageGroupId: String?, bytes: ByteArray): String {
        val opponent = loadOpponentForEdit(opponentRepository, ageGroupRepository, checkUserPermissionUseCase, transactionManager, userId, opponentId, ageGroupId)
        if (!imageStorage.isAvailable) throw ValidationException("Image uploads aren't set up on this server yet")
        val png = LogoImage.toPng(bytes)

        opponent.logoUrl?.let { OpponentLogos.keyOf(opponentId, it) }?.let { imageStorage.delete(it) }

        val imageId = Uuid.random().toString()
        try {
            imageStorage.put(OpponentLogos.key(opponentId, imageId), png, "image/png")
        } catch (e: Exception) {
            transactionManager.runInTransaction { opponentRepository.updateLogoUrl(opponentId, null) }
            throw e
        }
        val url = OpponentLogos.url(opponentId, imageId)
        transactionManager.runInTransaction { opponentRepository.updateLogoUrl(opponentId, url) }
        return url
    }
}

/** Removes an opponent's logo: the file goes, and the opponent shows its initials again. */
class DeleteOpponentLogoUseCase(
    private val opponentRepository: OpponentRepository,
    private val ageGroupRepository: AgeGroupRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, opponentId: String, ageGroupId: String?) {
        val opponent = loadOpponentForEdit(opponentRepository, ageGroupRepository, checkUserPermissionUseCase, transactionManager, userId, opponentId, ageGroupId)
        val logoUrl = opponent.logoUrl ?: return
        OpponentLogos.keyOf(opponentId, logoUrl)?.let { imageStorage.delete(it) }
        transactionManager.runInTransaction { opponentRepository.updateLogoUrl(opponentId, null) }
    }
}

/**
 * The logo's bytes, for anyone who can see the opponent's club (members, and players' families, who
 * see the club's matches). Only the current image id is served, so an old link stops working.
 */
class GetOpponentLogoUseCase(
    private val opponentRepository: OpponentRepository,
    private val checkUserPermissionUseCase: CheckUserPermissionUseCase,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, opponentId: String, imageId: String): ByteArray {
        val opponent = transactionManager.runInTransaction {
            val opponent = opponentRepository.getOpponentById(opponentId) ?: throw NotFoundException("Logo not found")
            checkUserPermissionUseCase(
                userId = userId,
                clubId = opponent.clubId,
                minClubRole = ClubRole.MEMBER,
                allowPlayerOrParentAccess = true,
                errorMessage = "You do not have access to this club"
            )
            opponent
        }
        if (opponent.logoUrl != OpponentLogos.url(opponentId, imageId)) throw NotFoundException("Logo not found")
        return imageStorage.get(OpponentLogos.key(opponentId, imageId)) ?: throw NotFoundException("Logo not found")
    }
}
