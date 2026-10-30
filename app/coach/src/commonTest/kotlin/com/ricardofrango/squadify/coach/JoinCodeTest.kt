package com.ricardofrango.squadify.coach

import com.ricardofrango.squadify.coach.domain.JoinCode
import com.ricardofrango.squadify.coach.presentation.ui.join.JoinWithCodeUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Typing an invite code (TECHNICAL_DECISIONS §72): any case, with or without the dash, and why the server
// refused one.
class JoinCodeTest {
    @Test
    fun aCodeIsTakenInAnyCaseWithOrWithoutTheDash() {
        assertEquals("K7Q4MZ", JoinCode.normalize("k7q-4mz"))
        assertEquals("K7Q4MZ", JoinCode.normalize(" K7Q 4MZ "))
        assertTrue(JoinCode.isComplete("k7q4mz"))
        assertFalse(JoinCode.isComplete("K7Q-4M"))
        assertFalse(JoinCode.isComplete("K7Q-4MZX"))
    }

    @Test
    fun joinIsOnlyOfferedForAWholeCodeNotWhileJoining() {
        assertFalse(JoinWithCodeUiState(code = "K7Q").canJoin)
        assertTrue(JoinWithCodeUiState(code = "K7Q-4MZ").canJoin)
        assertFalse(JoinWithCodeUiState(code = "K7Q-4MZ", isSubmitting = true).canJoin)
    }

    @Test
    fun eachRefusalHasItsOwnReason() {
        assertEquals(JoinCode.Refusal.NOT_FOUND, JoinCode.refusal(404))
        assertEquals(JoinCode.Refusal.EXPIRED, JoinCode.refusal(400))
        assertEquals(JoinCode.Refusal.USED_OR_ALREADY_ON, JoinCode.refusal(409))
        assertEquals(JoinCode.Refusal.OTHER, JoinCode.refusal(500))
        assertEquals(JoinCode.Refusal.OTHER, JoinCode.refusal(null))
    }
}
