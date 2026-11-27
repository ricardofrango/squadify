package com.sportssoftw.squadcall

// The API address the container sets at start (config.js, from the API_BASE_URL variable), or null when it
// isn't set, so the build's own address is used. See app/webApp/prebuilt/40-squadcall-config.sh.
internal expect fun runtimeApiBaseUrl(): String?
