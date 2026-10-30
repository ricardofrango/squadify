package com.ricardofrango.squadify.presentation.navigation

/**
 * Where an app opens once the user is signed in, for Splash and Login: each app binds its own in Koin
 * (TECHNICAL_DECISIONS §72). The clubs app: the user's only club, or the club list. The coach app: the
 * coach's space, or its first setup. Never throws: a failed check opens a screen that shows its own error.
 */
fun interface SignedInHome {
    suspend fun screen(): Screen
}
