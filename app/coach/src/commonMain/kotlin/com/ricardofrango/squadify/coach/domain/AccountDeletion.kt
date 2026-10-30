package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.model.AccountDeletionSummary

/** Deleting the coach's account on the coach server (`/v1/users/me/deletion`, `/delete`). */
interface AccountDeletionRepository {
    suspend fun getSummary(): AccountDeletionSummary
    suspend fun deleteAccount(currentPassword: String)
}

class GetAccountDeletionSummaryUseCase(private val repository: AccountDeletionRepository) {
    suspend operator fun invoke(): AccountDeletionSummary = repository.getSummary()
}

class DeleteAccountUseCase(private val repository: AccountDeletionRepository) {
    suspend operator fun invoke(currentPassword: String) = repository.deleteAccount(currentPassword)
}
