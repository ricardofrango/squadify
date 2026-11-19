package com.sportssoftw.squadify.model

/**
 * The headers each app sends with every request: its platform ("android" or "ios") and build number (TECHNICAL_DECISIONS §85).
 * The server refuses a build below its minimum with 426; the client's own build is shared with the server here.
 */
const val APP_PLATFORM_HEADER = "X-App-Platform"
const val APP_BUILD_HEADER = "X-App-Build"
