package com.ricardofrango.squadify.domain.service

interface TransactionManager {
    /**
     * Executes the given block within a database transaction.
     * If the block fails, the transaction is rolled back.
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
