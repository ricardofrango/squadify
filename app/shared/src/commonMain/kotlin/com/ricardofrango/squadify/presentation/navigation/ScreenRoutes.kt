package com.ricardofrango.squadify.presentation.navigation

import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// A screen's address on web, and in a shared link: the route Navigation 2 gave it, kept the same so
// links already shared keep opening (Screen.kt's @SerialName doc). The screen's @SerialName, then each
// argument without a default as a path segment, then those with one as query parameters:
// "club/abc?isRootClub=false", "match-report/42?edit=true". Values are URI-encoded; null is "null".

private val routeJson = Json { encodeDefaults = true }

/** Each screen's descriptor, by its @SerialName (the sealed class's subclasses). */
private val screenDescriptors: Map<String, SerialDescriptor> by lazy {
    val subclasses = Screen.serializer().descriptor.getElementDescriptor(1)
    (0 until subclasses.elementsCount).associate { subclasses.getElementName(it) to subclasses.getElementDescriptor(it) }
}

/** This screen's route, e.g. "team/123". */
fun Screen.toRoute(): String {
    val json = routeJson.encodeToJsonElement(Screen.serializer(), this).jsonObject
    val name = json.getValue("type").jsonPrimitive.content
    val descriptor = screenDescriptors.getValue(name)
    val path = StringBuilder(name)
    val query = mutableListOf<String>()
    for (i in 0 until descriptor.elementsCount) {
        val arg = descriptor.getElementName(i)
        val value = (json[arg] as? JsonPrimitive)?.let { if (it is JsonNull) "null" else it.content } ?: "null"
        if (descriptor.isElementOptional(i)) query += "${encodeUriComponent(arg)}=${encodeUriComponent(value)}"
        else path.append('/').append(encodeUriComponent(value))
    }
    return if (query.isEmpty()) path.toString() else path.append('?').append(query.joinToString("&")).toString()
}

/** The screen at [route] (as [toRoute] writes it), or null when it isn't one. */
fun screenFromRoute(route: String): Screen? = try {
    val trimmed = route.removePrefix("#").removePrefix("/")
    val pathPart = trimmed.substringBefore('?')
    val queryPart = trimmed.substringAfter('?', "")
    val segments = pathPart.split('/').map(::decodeUriComponent)
    val name = segments.first()
    val descriptor = screenDescriptors[name]
    if (descriptor == null) {
        null
    } else {
        val query = queryPart.split('&').filter { it.isNotEmpty() }.associate {
            decodeUriComponent(it.substringBefore('=')) to decodeUriComponent(it.substringAfter('=', ""))
        }
        val pathValues = segments.drop(1).iterator()
        val fields = mutableMapOf<String, JsonElement>("type" to JsonPrimitive(name))
        for (i in 0 until descriptor.elementsCount) {
            val arg = descriptor.getElementName(i)
            val raw = if (descriptor.isElementOptional(i)) query[arg] ?: continue else if (pathValues.hasNext()) pathValues.next() else return null
            fields[arg] = when {
                raw == "null" && descriptor.getElementDescriptor(i).isNullable -> JsonNull
                descriptor.getElementDescriptor(i).kind == PrimitiveKind.BOOLEAN -> JsonPrimitive(raw.toBooleanStrict())
                else -> JsonPrimitive(raw)
            }
        }
        if (pathValues.hasNext()) null else routeJson.decodeFromJsonElement(Screen.serializer(), JsonObject(fields))
    }
} catch (e: IllegalArgumentException) {
    null // a malformed or unknown route (SerializationException is one)
}

private const val Unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
private const val Hex = "0123456789ABCDEF"

/** JavaScript's encodeURIComponent, for every platform. */
internal fun encodeUriComponent(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        if (c < 0x80 && c.toChar() in Unreserved) append(c.toChar())
        else append('%').append(Hex[c shr 4]).append(Hex[c and 0xF])
    }
}

/** JavaScript's decodeURIComponent, for every platform. A malformed escape is kept as written. */
internal fun decodeUriComponent(value: String): String {
    if ('%' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        val hex = if (c == '%' && i + 2 <= value.lastIndex) value.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            bytes += hex.toByte()
            i += 3
        } else {
            c.toString().encodeToByteArray().forEach { bytes += it }
            i++
        }
    }
    return bytes.toByteArray().decodeToString()
}
