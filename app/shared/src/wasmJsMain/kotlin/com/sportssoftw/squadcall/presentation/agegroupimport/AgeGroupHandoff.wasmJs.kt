package com.sportssoftw.squadcall.presentation.agegroupimport

import androidx.compose.runtime.Composable

// Copying an age group between the apps is Android only (the apps' handoff is an Android activity).
@Composable
actual fun rememberAgeGroupHandoff(targetPackages: List<String>, onGranted: (AgeGroupHandoff) -> Unit): AgeGroupHandoffLauncher =
    object : AgeGroupHandoffLauncher {
        override val installed: Boolean = false
        override fun start() = Unit
    }
