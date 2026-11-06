package com.ricardofrango.squadify.presentation.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.acceptLanguageItems

/**
 * The language of the app that made this request ("pt", "en"), for what the server writes for it:
 * emails and the plans' features. Both apps send it as `Accept-Language` on every request; a
 * `?language=` parameter, which some coach calls sent before that header, still counts first. Null
 * when there's neither (an old app), which means English.
 */
fun ApplicationCall.appLanguage(): String? =
    request.queryParameters["language"]?.takeIf { it.isNotBlank() }
        ?: request.acceptLanguageItems().firstOrNull()?.value
