package com.sportssoftw.squadcall

internal actual fun runtimeApiBaseUrl(): String? =
    js("(window.SQUADCALL_CONFIG && window.SQUADCALL_CONFIG.apiBaseUrl) || null") as String?
