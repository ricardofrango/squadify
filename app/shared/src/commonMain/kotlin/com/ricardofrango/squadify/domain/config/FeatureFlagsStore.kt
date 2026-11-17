package com.ricardofrango.squadify.domain.config

import com.ricardofrango.squadify.domain.repository.FeatureFlagsRepository
import com.ricardofrango.squadify.model.FeatureFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where the flags stand: not read yet, read, or the last read failed (the app then waits for a connection). */
enum class FeatureFlagsStatus { LOADING, LOADED, FAILED }

/**
 * The flags the server turned on, as the app last read them (TECHNICAL_DECISIONS §82). No screen opens until they
 * have been read (the root gate in each app): a read that fails leaves the status [FeatureFlagsStatus.FAILED], and
 * the app waits for a connection and reads again. Once read, a later failed read keeps the flags and the status.
 */
class FeatureFlagsStore(private val repository: FeatureFlagsRepository) {
    private val _flags = MutableStateFlow(FeatureFlags())
    val flags: StateFlow<FeatureFlags> = _flags.asStateFlow()

    private val _status = MutableStateFlow(FeatureFlagsStatus.LOADING)
    val status: StateFlow<FeatureFlagsStatus> = _status.asStateFlow()

    /** Reads the flags from the server: once, at the start, or again after a failure. */
    suspend fun load() {
        if (_status.value == FeatureFlagsStatus.LOADED) return
        _status.update { FeatureFlagsStatus.LOADING }
        try {
            _flags.value = repository.load()
            _status.value = FeatureFlagsStatus.LOADED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = FeatureFlagsStatus.FAILED
        }
    }

    /** Reads again, when a read has failed (a connection came back, the app is in front, or Retry). */
    suspend fun retryIfFailed() {
        if (_status.value == FeatureFlagsStatus.FAILED) load()
    }
}
