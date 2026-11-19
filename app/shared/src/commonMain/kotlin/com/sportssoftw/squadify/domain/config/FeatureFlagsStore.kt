package com.sportssoftw.squadify.domain.config

import com.sportssoftw.squadify.domain.repository.FeatureFlagsRepository
import com.sportssoftw.squadify.model.FeatureFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where the flags stand: not read yet, read, the last read failed (the app then waits for a connection), or this build is
 * too old and must update (the flags say so, or the server refused a request with 426; TECHNICAL_DECISIONS §85).
 */
enum class FeatureFlagsStatus { LOADING, LOADED, FAILED, UPDATE_REQUIRED }

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

    /** Reads the flags from the server: once, at the start, or again after a failure or an update. */
    suspend fun load() {
        if (_status.value == FeatureFlagsStatus.LOADED) return
        // Staying on the update screen while it reads again: no splash in between.
        val updateRequired = _status.value == FeatureFlagsStatus.UPDATE_REQUIRED
        if (!updateRequired) _status.value = FeatureFlagsStatus.LOADING
        try {
            val flags = repository.load()
            _flags.value = flags
            _status.value = if (flags.updateRequired) FeatureFlagsStatus.UPDATE_REQUIRED else FeatureFlagsStatus.LOADED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = if (updateRequired) FeatureFlagsStatus.UPDATE_REQUIRED else FeatureFlagsStatus.FAILED
        }
    }

    /** Reads again, when a read has failed or the build must update (a connection came back, the app is in front, or Retry). */
    suspend fun retryIfFailed() {
        if (_status.value == FeatureFlagsStatus.FAILED || _status.value == FeatureFlagsStatus.UPDATE_REQUIRED) load()
    }

    /** The server refused this build (426): the update screen takes over, whatever the flags said before. */
    fun reportUpdateRequired() {
        _status.value = FeatureFlagsStatus.UPDATE_REQUIRED
    }
}
