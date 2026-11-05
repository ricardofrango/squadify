package com.ricardofrango.squadify.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records what the clubs app runs from a cold start to its first screen, so the release build can
 * have it compiled ahead of time. The app is installed fresh, so the first screen is sign-in.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        // The app's id, set per flavor in this module's build file.
        packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not passed as an instrumentation argument"),
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
    }
}
