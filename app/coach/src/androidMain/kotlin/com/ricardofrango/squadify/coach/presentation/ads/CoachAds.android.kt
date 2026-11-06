package com.ricardofrango.squadify.coach.presentation.ads

import com.ricardofrango.squadify.coach.domain.CoachAds
import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.koin.compose.koinInject
import java.util.concurrent.atomic.AtomicBoolean

/** An anchored adaptive banner, as wide as the space it's given, its height Google's for that width. */
@Composable
actual fun PlatformAdBanner(adUnitId: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val width = maxWidth.value.toInt()
        val size = remember(width) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width) }
        val view = remember(adUnitId, size) {
            AdView(context).apply {
                setAdSize(size)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
        DisposableEffect(view) { onDispose { view.destroy() } }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(size.height.dp))
    }
}

@Composable
actual fun AdsConsentEffect() {
    val activity = LocalActivity.current ?: return
    val ads = koinInject<CoachAds>()
    val withAds by ads.spacesWithAds.collectAsStateWithLifecycle()
    val needed = withAds.isNotEmpty()
    LaunchedEffect(needed) { if (needed) AdsConsent.gather(activity, ads) }
}

@Composable
actual fun rememberPrivacyOptions(): (() -> Unit)? {
    val activity = LocalActivity.current ?: return null
    val required by koinInject<CoachAds>().privacyOptionsRequired.collectAsStateWithLifecycle()
    return if (required) { { UserMessagingPlatform.showPrivacyOptionsForm(activity) {} } } else null
}

/**
 * Google's consent flow: the consent information is updated each time a space with ads shows up, and the
 * form shown when it's required (the EU and UK, the first time, or when Google asks again). Ads may load
 * once [ConsentInformation.canRequestAds]: with the consent from before right away, else after the form.
 * The ads SDK starts once, its ads rated for a general audience (PG), the app's users being coaches of
 * children's teams.
 */
private object AdsConsent {
    private val started = AtomicBoolean(false)

    fun gather(activity: Activity, ads: CoachAds) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    ads.privacyOptionsRequired.value =
                        consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    startAds(activity, consent, ads)
                }
            },
            // Can't reach Google: the consent from before, if any, still counts.
            { startAds(activity, consent, ads) }
        )
        // A consent given before needn't wait for the update.
        startAds(activity, consent, ads)
    }

    private fun startAds(activity: Activity, consent: ConsentInformation, ads: CoachAds) {
        if (!consent.canRequestAds()) return
        if (!started.compareAndSet(false, true)) {
            return
        }
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG).build()
        )
        // Off the main thread, as Google advises: starting the SDK can take a moment.
        val context = activity.applicationContext
        Thread { MobileAds.initialize(context) { ads.canRequestAds.value = true } }.start()
    }
}
