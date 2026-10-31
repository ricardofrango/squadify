package com.ricardofrango.squadify.presentation.navigation

import com.ricardofrango.squadify.model.PushLink

/**
 * Where an app opens once the user is signed in, for Splash and Login: each app binds its own in Koin
 * (TECHNICAL_DECISIONS §72). The clubs app: the user's only club, or the club list. The coach app: the
 * coach's age groups. Never throws: a failed check opens a screen that shows its own error.
 */
interface SignedInHome {
    suspend fun screen(): Screen

    /**
     * What a tapped push notification opens, bottom first: the app's own screens under the target
     * ([pushLinkTarget]), so Back lands somewhere that app has. [home] is what [screen] just answered.
     */
    fun pushLinkBackStack(link: PushLink, home: Screen): List<Screen>
}
