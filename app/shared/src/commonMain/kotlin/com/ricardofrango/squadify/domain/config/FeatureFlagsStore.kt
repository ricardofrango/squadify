package com.ricardofrango.squadify.domain.config

import com.ricardofrango.squadify.domain.repository.FeatureFlagsRepository
import com.ricardofrango.squadify.model.FeatureFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The flags the server turned on, as the app last read them. Everything is off until the first read answers, and a
 * read that fails keeps what it had: a feature is never shown on a guess.
 */
class FeatureFlagsStore(private val repository: FeatureFlagsRepository) {
    private val _flags = MutableStateFlow(FeatureFlags())
    val flags: StateFlow<FeatureFlags> = _flags.asStateFlow()

    /** Reads the flags from the server, at startup. */
    suspend fun refresh() {
        try {
            _flags.value = repository.load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the flags we had.
        }
    }
}
