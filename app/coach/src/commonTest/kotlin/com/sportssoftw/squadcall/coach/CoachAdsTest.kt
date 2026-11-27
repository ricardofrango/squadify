package com.sportssoftw.squadcall.coach

import com.sportssoftw.squadcall.coach.domain.CoachAds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The banner's unit: the feature flags' unit (AD_BANNER_UNIT_ID_ANDROID) wins over the app's build, and the build's
// is the fallback, so a unit can change without a release and the app still shows ads when the flags give none.
class CoachAdsTest {
    @Test
    fun theBuildsUnitIsUsedWhenTheFlagsGiveNone() {
        val ads = CoachAds { null }
        ads.buildBannerUnitId = "build-unit"
        assertEquals("build-unit", ads.bannerUnitId)
    }

    @Test
    fun theFlagsUnitWinsOverTheBuilds() {
        val ads = CoachAds { "flag-unit" }
        ads.buildBannerUnitId = "build-unit"
        assertEquals("flag-unit", ads.bannerUnitId)
    }

    @Test
    fun aBlankFlagFallsBackToTheBuild() {
        val ads = CoachAds { "  " }
        ads.buildBannerUnitId = "build-unit"
        assertEquals("build-unit", ads.bannerUnitId)
    }

    @Test
    fun noUnitWhenNeitherSetsOne() {
        assertNull(CoachAds().bannerUnitId)
    }
}
