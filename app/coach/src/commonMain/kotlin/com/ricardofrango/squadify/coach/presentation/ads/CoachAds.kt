package com.ricardofrango.squadify.coach.presentation.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ricardofrango.squadify.coach.domain.CoachAds
import org.koin.compose.koinInject

/** The banner for [spaceId]'s screens: nothing when its plan has no ads, before consent, or without a unit. */
@Composable
fun CoachAdBanner(spaceId: String?) {
    val ads = koinInject<CoachAds>()
    val withAds by ads.spacesWithAds.collectAsStateWithLifecycle()
    val ready by ads.canRequestAds.collectAsStateWithLifecycle()
    val unit = ads.bannerUnitId
    if (spaceId == null || spaceId !in withAds || !ready || unit == null) return
    PlatformAdBanner(unit)
}

/** The platform's banner (Android: an adaptive AdMob banner, the window's width); nothing elsewhere. */
@Composable
expect fun PlatformAdBanner(adUnitId: String)

/**
 * Asks for consent (Google's UMP form, in the EU and UK) once a space has ads, then starts the ads SDK
 * and sets [CoachAds.canRequestAds]. Android only; nothing elsewhere.
 */
@Composable
expect fun AdsConsentEffect()

/** Opens the consent form again, for the menu's Privacy options; null where it isn't required. */
@Composable
expect fun rememberPrivacyOptions(): (() -> Unit)?
