package com.ricardofrango.squadify.presentation.agegroupimport

import androidx.compose.runtime.Composable

/**
 * The handoff between the two apps (TECHNICAL_DECISIONS §81). The importing app opens the other app's
 * [EXPORT_AGE_GROUPS_ACTIVITY]; that app asks its user to allow the copy and returns a grant and its server's address.
 */
data class AgeGroupHandoff(val grant: String, val sourceUrl: String)

/** The Android class of the consent screen, in each app (the same class, in the shared Android module). */
const val EXPORT_AGE_GROUPS_ACTIVITY = "com.ricardofrango.squadify.presentation.agegroupimport.ExportAgeGroupsActivity"

/** The result extras: the grant and the exporting server's address (its origin, without the /v1 prefix). */
const val EXTRA_EXPORT_GRANT = "squadify.extra.EXPORT_GRANT"
const val EXTRA_EXPORT_SOURCE_URL = "squadify.extra.EXPORT_SOURCE_URL"

/** The clubs app's package names (prod, staging, dev), and the coach app's. */
val CLUBS_APP_PACKAGES = listOf("com.ricardofrango.squadify", "com.ricardofrango.squadify.stg", "com.ricardofrango.squadify.dev")
val COACH_APP_PACKAGES = listOf("com.ricardofrango.squadify.coach", "com.ricardofrango.squadify.coach.stg", "com.ricardofrango.squadify.coach.dev")

/** Whether the other app is installed here, and how to ask it for a grant. Off Android, never installed. */
interface AgeGroupHandoffLauncher {
    val installed: Boolean
    fun start()
}

/** Asks the first of [targetPackages] that is installed for a grant; [onGranted] gets it back from that app. */
@Composable
expect fun rememberAgeGroupHandoff(targetPackages: List<String>, onGranted: (AgeGroupHandoff) -> Unit): AgeGroupHandoffLauncher
