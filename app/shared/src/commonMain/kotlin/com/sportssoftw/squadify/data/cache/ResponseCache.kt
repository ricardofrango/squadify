package com.sportssoftw.squadify.data.cache

import androidx.compose.ui.text.intl.Locale
import com.sportssoftw.squadify.data.remote.handleResponse
import com.sportssoftw.squadify.model.FeatureFlags
import com.sportssoftw.squadify.domain.repository.SessionRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import kotlin.io.encoding.Base64
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Which period a read is kept for: the general one, or the shorter one for a game's status and results (TECHNICAL_DECISIONS §83). */
enum class CacheKind { GENERAL, MATCH }

/**
 * The marker a pull-to-refresh puts on the coroutine it starts (`launch(ForceRefresh)`): the reads under it go to the
 * server, and their answers are kept again. Coroutines launched from it inherit it.
 */
object ForceRefresh : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ForceRefresh>
}

/**
 * The reads kept in memory (TECHNICAL_DECISIONS §83), for the signed-in user only. A key is the user (the `sub` claim
 * of the access token), the address and the app's language, so one user never gets another's answer. The periods come
 * from the server's feature flags; 0 turns caching off. Only successful answers are kept; a write clears them all.
 */
class ResponseCache(
    private val sessionRepository: SessionRepository,
    // The periods the server's feature flags give now (see FeatureFlagsStore).
    private val flags: () -> FeatureFlags,
    // The clock; a test moves it.
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }
) {
    private class Entry(val text: String, val storedAtMillis: Long)

    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, Entry>()

    /** The answer for [url] from [kind]'s period, or a fresh one from [fetch] (kept for next time). */
    suspend fun load(url: String, kind: CacheKind, fetch: suspend () -> String): String {
        val seconds = when (kind) {
            CacheKind.GENERAL -> flags().cacheSeconds
            CacheKind.MATCH -> flags().matchCacheSeconds
        }
        val key = keyFor(url)
        if (seconds <= 0 || key == null) return fetch()
        val at = now()
        val forced = coroutineContext[ForceRefresh.Key] != null
        if (!forced) {
            mutex.withLock {
                entries[key]?.takeIf { at - it.storedAtMillis < seconds * 1000L }?.let { return it.text }
            }
        }
        val text = fetch()
        mutex.withLock {
            entries.remove(key)
            entries[key] = Entry(text, at)
            // The oldest answers go first, so the cache stays small.
            while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
        }
        return text
    }

    /** Forgets every answer: after a change, a logout, or a login. */
    suspend fun clear() = mutex.withLock { entries.clear() }

    /** The key for [url] for the signed-in user, or null when nobody is signed in (nothing is kept then). */
    private suspend fun keyFor(url: String): String? {
        val user = sessionRepository.getToken()?.let(::userIdOf) ?: return null
        return "$user|${Locale.current.language}|$url"
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}

/** The key the authenticated client carries its cache under. */
val ResponseCacheKey = AttributeKey<ResponseCache>("ResponseCache")

// Read with the app's own settings (the client's JSON: unknown fields ignored, lenient).
@PublishedApi
internal val cacheJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * The answer for [url] (a GET, with [block]'s parameters), decoded as [T]. Kept under [kind]'s period when the client
 * has a cache (the authenticated one does), otherwise always from the server. A failed answer throws, as
 * `handleResponse` does. The key is the whole address, parameters included, so each page of a list has its own.
 */
suspend inline fun <reified T> HttpClient.cachedGet(
    url: String,
    kind: CacheKind = CacheKind.GENERAL,
    noinline block: HttpRequestBuilder.() -> Unit = {}
): T {
    val text = responseCacheOrNull()?.load(requestKeyOf(url, block), kind) { fetchText(url, block) } ?: fetchText(url, block)
    return cacheJson.decodeFromString(text)
}

/** The full address of a request: the URL with the parameters [block] adds. */
@PublishedApi
internal fun requestKeyOf(address: String, block: HttpRequestBuilder.() -> Unit): String =
    HttpRequestBuilder().apply { url(address); block() }.url.build().toString()

/** Forgets the answers the client's cache holds, if it has one. */
suspend fun HttpClient.clearResponseCache() {
    responseCacheOrNull()?.clear()
}

/** The cache the client carries, or null for a client without one. */
fun HttpClient.responseCacheOrNull(): ResponseCache? =
    if (attributes.contains(ResponseCacheKey)) attributes[ResponseCacheKey] else null

/** The body of [url] as text, or the error `handleResponse` throws for a failed answer. */
suspend fun HttpClient.fetchText(url: String, block: HttpRequestBuilder.() -> Unit = {}): String {
    val response = get(url) { block() }
    if (!response.status.isSuccess()) response.handleResponse<String>()
    return response.bodyAsText()
}

/** The user id: the `sub` claim of the access token (a JWT). Null for anything that isn't one. */
internal fun userIdOf(token: String): String? {
    val parts = token.split('.')
    if (parts.size != 3) return null
    return try {
        val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(parts[1]).decodeToString()
        cacheJson.parseToJsonElement(payload).jsonObject["sub"]?.jsonPrimitive?.content
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** Config for [ClearResponseCacheOnWrites]: the cache whose answers a successful write makes out of date. */
class ClearResponseCacheConfig {
    lateinit var cache: ResponseCache
}

/**
 * Clears the cache after a successful write (anything but a GET), so a change shows at once. The token refresh is
 * a POST too, but it changes no data, so it doesn't clear.
 */
val ClearResponseCacheOnWrites = createClientPlugin("ClearResponseCacheOnWrites", ::ClearResponseCacheConfig) {
    val cache = pluginConfig.cache
    onResponse { response ->
        val request = response.call.request
        val refresh = request.url.encodedPath.endsWith("/auth/refresh")
        if (request.method != HttpMethod.Get && response.status.isSuccess() && !refresh) cache.clear()
    }
}
