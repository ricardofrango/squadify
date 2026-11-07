package com.ricardofrango.squadify.coach.presentation.ads

import androidx.compose.runtime.Composable

// No ads on iOS yet: it launches after Android (REQUIREMENTS, launch readiness).

@Composable
actual fun PlatformAdBanner(adUnitId: String) {}

@Composable
actual fun AdsConsentEffect() {}

@Composable
actual fun rememberPrivacyOptions(): (() -> Unit)? = null
