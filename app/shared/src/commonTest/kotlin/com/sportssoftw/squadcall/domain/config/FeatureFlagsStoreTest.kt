package com.sportssoftw.squadcall.domain.config

import com.sportssoftw.squadcall.domain.repository.FeatureFlagsRepository
import com.sportssoftw.squadcall.model.FeatureFlags
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The flags' read at start (TECHNICAL_DECISIONS §82): the app waits for a successful read, a failed one leaves the
 * status failed until a later read succeeds, and a failure after a success keeps the app open.
 */
class FeatureFlagsStoreTest {

    /** Answers with [answers] in order: a flags object, or null for a failed read. */
    private class FakeFlags(vararg answers: FeatureFlags?) : FeatureFlagsRepository {
        private val queue = answers.toMutableList()
        var reads = 0
        override suspend fun load(): FeatureFlags {
            reads++
            return queue.removeFirst() ?: throw IllegalStateException("no connection")
        }
    }

    @Test
    fun startsLoadingAndLoadedOnASuccessfulRead() = runTest {
        val store = FeatureFlagsStore(FakeFlags(FeatureFlags(ageGroupImport = true, cacheSeconds = 3600)))
        assertEquals(FeatureFlagsStatus.LOADING, store.status.value)
        store.load()
        assertEquals(FeatureFlagsStatus.LOADED, store.status.value)
        assertEquals(FeatureFlags(ageGroupImport = true, cacheSeconds = 3600), store.flags.value)
    }

    @Test
    fun aFailedReadLeavesTheAppWaitingAndAReadAfterwardsOpensIt() = runTest {
        val repository = FakeFlags(null, FeatureFlags(ageGroupImport = true))
        val store = FeatureFlagsStore(repository)
        store.load()
        assertEquals(FeatureFlagsStatus.FAILED, store.status.value)
        store.retryIfFailed()
        assertEquals(FeatureFlagsStatus.LOADED, store.status.value)
        assertEquals(2, repository.reads)
    }

    @Test
    fun retryDoesNothingWhenTheFlagsAreAlreadyRead() = runTest {
        val repository = FakeFlags(FeatureFlags())
        val store = FeatureFlagsStore(repository)
        store.load()
        store.retryIfFailed()
        store.load()
        assertEquals(1, repository.reads)
        assertEquals(FeatureFlagsStatus.LOADED, store.status.value)
    }

    @Test
    fun onceReadTheAppStaysOpenWithItsFlagsAndDoesNotReadAgain() = runTest {
        val repository = FakeFlags(FeatureFlags(ageGroupImport = true), null)
        val store = FeatureFlagsStore(repository)
        store.load()
        store.retryIfFailed()
        store.load()
        assertEquals(FeatureFlagsStatus.LOADED, store.status.value)
        assertEquals(FeatureFlags(ageGroupImport = true), store.flags.value)
        assertEquals(1, repository.reads)
    }

    // A build below its minimum (TECHNICAL_DECISIONS §85): the update screen, until a read says the build is current.
    @Test
    fun updateRequiredFromTheFlagsShowsTheUpdateScreenUntilARead() = runTest {
        val store = FeatureFlagsStore(FakeFlags(FeatureFlags(updateRequired = true), FeatureFlags(updateRequired = false)))
        store.load()
        assertEquals(FeatureFlagsStatus.UPDATE_REQUIRED, store.status.value)
        store.retryIfFailed()
        assertEquals(FeatureFlagsStatus.LOADED, store.status.value)
    }

    @Test
    fun aRefusedRequestShowsTheUpdateScreenAfterTheFlagsLoaded() = runTest {
        val store = FeatureFlagsStore(FakeFlags(FeatureFlags(), FeatureFlags()))
        store.load()
        store.reportUpdateRequired()
        assertEquals(FeatureFlagsStatus.UPDATE_REQUIRED, store.status.value)
        store.retryIfFailed()
        assertEquals(FeatureFlagsStatus.LOADED, store.status.value)
    }

    @Test
    fun aFailedReadWhileUpdatingKeepsTheUpdateScreen() = runTest {
        val store = FeatureFlagsStore(FakeFlags(FeatureFlags(updateRequired = true), null))
        store.load()
        store.retryIfFailed()
        assertEquals(FeatureFlagsStatus.UPDATE_REQUIRED, store.status.value)
    }
}
