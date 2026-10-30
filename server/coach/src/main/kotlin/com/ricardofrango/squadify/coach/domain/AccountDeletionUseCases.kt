package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.coach.data.AccountDeletionRepository
import com.ricardofrango.squadify.domain.service.CurrentPasswordGuard
import com.ricardofrango.squadify.domain.service.TransactionManager
import com.ricardofrango.squadify.model.AccountDeletionSummary
import com.ricardofrango.squadify.model.DeleteAccountRequest

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
 * stolen access token alone can't delete it. All in one transaction: a failure deletes nothing.
 */
class DeleteAccountUseCase(
    private val accountDeletion: AccountDeletionRepository,
    private val currentPasswordGuard: CurrentPasswordGuard,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: String, request: DeleteAccountRequest) {
        currentPasswordGuard.verify(userId, request.currentPassword)
        transactionManager.runInTransaction { accountDeletion.delete(userId) }
    }
}
