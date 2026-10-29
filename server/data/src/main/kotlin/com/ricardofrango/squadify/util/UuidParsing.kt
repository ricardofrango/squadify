package com.ricardofrango.squadify.util

import com.ricardofrango.squadify.exception.IllegalIdFormatException
import kotlin.uuid.Uuid

/**
 * Parses this string as a [Uuid], throwing [IllegalIdFormatException] (-> 400, see
 * `Application.kt`'s `StatusPages`) instead of letting `Uuid.parse`'s raw `IllegalArgumentException`
 * escape as an unhandled 500. Every repository parses path/body-sourced ids this way rather than
 * calling `Uuid.parse` directly -- see "issues to fix.md", "Unguarded `Uuid.parse(...)` everywhere".
 */
fun String.toUuid(): Uuid = try {
    Uuid.parse(this)
} catch (e: IllegalArgumentException) {
    throw IllegalIdFormatException("Invalid ID format: '$this'")
}
