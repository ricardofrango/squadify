package com.ricardofrango.squadify.coach.domain

import com.ricardofrango.squadify.model.SpaceAgeGroups
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ads on the Free plan (REQUIREMENTS §5c): a banner under the browsing screens of a space whose plan has
 * them, never in a live game, a form or Plans. Which spaces do is the coach server's (`SpaceAgeGroups.
 * showsAds`), kept here with each load of the spaces ([remember]); the app decides no plan rule itself.
 * Ads only load once the consent form has been answered and the ads SDK has started ([canRequestAds]),
 * which the platform sets (`AdsConsentEffect`); the form is only asked for once a space has ads.
 */
class CoachAds {
    /** The spaces whose screens show a banner. */
    val spacesWithAds = MutableStateFlow<Set<String>>(emptySet())

    /** The space Home stands for: the coach's own, else the first they're in. */
    val homeSpaceId = MutableStateFlow<String?>(null)

    /** Consent answered (or not needed) and the ads SDK started. */
    val canRequestAds = MutableStateFlow(false)

    /** Whether the user must be able to change their consent (the EU and UK): the menu's Privacy options. */
    val privacyOptionsRequired = MutableStateFlow(false)

    /** The banner's ad unit, from the app's build (Google's test unit until the real one). */
    var bannerUnitId: String? = null

    fun remember(spaces: List<SpaceAgeGroups>) {
        spacesWithAds.value = spaces.filter { it.showsAds }.map { it.space.id }.toSet()
        homeSpaceId.value = (spaces.firstOrNull { it.isOwner } ?: spaces.firstOrNull())?.space?.id
    }
}

