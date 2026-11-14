package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.AccountDeletionRepository
import com.ricardofrango.squadify.domain.access.AccessPolicy
import com.ricardofrango.squadify.domain.service.CurrentPasswordGuard
import com.ricardofrango.squadify.domain.service.ImageStorage
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AccountDeletionSummary
import com.ricardofrango.squadify.model.DeleteAccountRequest
import com.ricardofrango.squadify.model.DeleteSpaceRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** What deleting the caller's account would take with it, for the app's warning. */
class GetAccountDeletionSummaryUseCase(
    private val accountDeletion: AccountDeletionRepository,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String): AccountDeletionSummary = transactionManager.runInTransaction {
        accountDeletion.ownSpace(userId)?.let {
            AccountDeletionSummary(it.name, it.ageGroups, it.players, it.matches, it.staff)
        } ?: AccountDeletionSummary()
    }
}

/**
 * Deletes the caller's account at once and for good (REQUIREMENTS §5c; the stores require it): their own
 * space with everything in it, their place in other spaces, and the account. The current password is
 * checked first ([CurrentPasswordGuard]: a wrong one is a 400, the third in a row signs them out), so a
 * stolen access token alone can't delete it. All in one transaction: a failure deletes nothing. The
 * images' files go once the rows are gone; one that can't be deleted is logged and left (nothing points
 * at it any more), rather than failing a deletion that's already done.
 */
class DeleteAccountUseCase(
    private val accountDeletion: AccountDeletionRepository,
    private val currentPasswordGuard: CurrentPasswordGuard,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, request: DeleteAccountRequest) {
        currentPasswordGuard.verify(userId, request.currentPassword)
        val images = transactionManager.runInTransaction { accountDeletion.delete(userId) }
        images.forEach { key ->
            try {
                imageStorage.delete(key)
            } catch (e: Exception) {
                logger.warn("Couldn't delete image {} of a deleted account", key, e)
            }
        }
    }

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(DeleteAccountUseCase::class.java)
    }
}

/**
 * Deletes a space at its owner's request (REQUIREMENTS §5c): the space with its age groups, teams, players,
 * matches, training and staff access, all at once and for good. The owner keeps their account and any other
 * space they're staff in. The current password is checked first, as for the account. Only the owner can:
 * [AccessPolicy.requireEditClub] is the owner's check on the coach server. One transaction, as the account's;
 * the images go once the rows are gone.
 */
class DeleteSpaceUseCase(
    private val accessPolicy: AccessPolicy,
    private val accountDeletion: AccountDeletionRepository,
    private val currentPasswordGuard: CurrentPasswordGuard,
    private val transactionManager: TransactionManager,
    private val imageStorage: ImageStorage
) {
    suspend operator fun invoke(userId: String, spaceId: String, request: DeleteSpaceRequest) {
        currentPasswordGuard.verify(userId, request.currentPassword)
        accessPolicy.requireEditClub(userId, spaceId, OWNER_ONLY_MESSAGE)
        val images = transactionManager.runInTransaction { accountDeletion.deleteSpace(spaceId) }
        images.forEach { key ->
            try {
                imageStorage.delete(key)
            } catch (e: Exception) {
                logger.warn("Couldn't delete image {} of a deleted space", key, e)
            }
        }
    }

    private companion object {
        const val OWNER_ONLY_MESSAGE = "Only the space's owner can delete it"
        val logger: Logger = LoggerFactory.getLogger(DeleteSpaceUseCase::class.java)
    }
}
