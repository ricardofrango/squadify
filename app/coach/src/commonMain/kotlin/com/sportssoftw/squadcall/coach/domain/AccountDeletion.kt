package com.sportssoftw.squadcall.coach.domain

import com.sportssoftw.squadcall.model.AccountDeletionSummary

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
