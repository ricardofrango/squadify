package com.ricardofrango.squadify.domain.usecase

import com.ricardofrango.squadify.domain.repository.OpponentRepository
import com.ricardofrango.squadify.domain.repository.PlayerRepository
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.exception.NotFoundException
import kotlin.time.Duration


/** An opponent's current logo for a goal push (`/v1/public/opponents/{opponentId}/logo/{imageId}.png`). */
class GetPublicOpponentLogoUseCase(
    private val opponentRepository: OpponentRepository,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(opponentId: String, imageId: String): ByteArray {
        val opponent = transactionManager.runInTransaction { opponentRepository.getOpponentById(opponentId) } ?: throw NotFoundException("Logo not found")
        if (opponent.logoUrl != OpponentLogos.url(opponentId, imageId)) throw NotFoundException("Logo not found")
        return imageStorage.get(OpponentLogos.key(opponentId, imageId)) ?: throw NotFoundException("Logo not found")
    }
}

/**
 * A scorer's current photo for a goal push (`/v1/public/players/{playerId}/photo/{imageId}.png?exp=…&sig=…`):
 * only with a valid, unexpired signature from [PublicImageLinks.playerPhoto]. A bad or old link is a 404,
 * like a missing photo, so it says nothing about whether one exists.
 */
class GetPublicPlayerPhotoUseCase(
    private val playerRepository: PlayerRepository,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage,
    private val publicImageLinks: PublicImageLinks
) {
    suspend operator fun invoke(playerId: String, imageId: String, expires: Long?, sig: String?): ByteArray {
        if (expires == null || sig == null || !publicImageLinks.isValidPhotoLink(playerId, imageId, expires, sig)) throw NotFoundException("Photo not found")
        val player = transactionManager.runInTransaction { playerRepository.getPlayerById(playerId) } ?: throw NotFoundException("Photo not found")
        if (player.photoUrl != PlayerPhotos.url(playerId, imageId)) throw NotFoundException("Photo not found")
        return imageStorage.get(PlayerPhotos.key(playerId, imageId)) ?: throw NotFoundException("Photo not found")
    }
}
