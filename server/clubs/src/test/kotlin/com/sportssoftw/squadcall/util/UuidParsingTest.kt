package com.sportssoftw.squadcall.util

import com.sportssoftw.squadcall.exception.IllegalIdFormatException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/**
 * Every repository parses path/body-sourced ids via this extension instead of calling `Uuid.parse`
 * directly, so a malformed id throws a known SquadcallException (-> 400) instead of a raw
 * IllegalArgumentException escaping as an unhandled 500. See "issues to fix.md", "Unguarded
 * `Uuid.parse(...)` everywhere".
 */
class UuidParsingTest {

    @Test
    fun aValidUuidStringParsesCorrectly() {
        val id = Uuid.random()

        assertEquals(id, id.toString().toUuid())
    }

    @Test
    fun aMalformedStringThrowsIllegalIdFormatException() {
        assertFailsWith<IllegalIdFormatException> {
            "not-a-real-uuid".toUuid()
        }
    }

    @Test
    fun aBlankStringThrowsIllegalIdFormatException() {
        assertFailsWith<IllegalIdFormatException> {
            "".toUuid()
        }
    }
}
