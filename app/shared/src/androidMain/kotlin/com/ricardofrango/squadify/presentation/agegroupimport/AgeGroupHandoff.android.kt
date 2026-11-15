package com.ricardofrango.squadify.presentation.agegroupimport

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberAgeGroupHandoff(targetPackages: List<String>, onGranted: (AgeGroupHandoff) -> Unit): AgeGroupHandoffLauncher {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    // Package visibility: the apps declare these packages in <queries>, so an installed one is found.
    val installedPackage = remember(context, targetPackages) { targetPackages.firstOrNull { context.isInstalled(it) } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val grant = result.data?.getStringExtra(EXTRA_EXPORT_GRANT)
            val source = result.data?.getStringExtra(EXTRA_EXPORT_SOURCE_URL)
            if (grant != null && source != null) currentOnGranted(AgeGroupHandoff(grant, source))
        }
    }
    return remember(installedPackage, launcher) {
        object : AgeGroupHandoffLauncher {
            override val installed: Boolean = installedPackage != null
            override fun start() {
                val target = installedPackage ?: return
                launcher.launch(Intent().setClassName(target, EXPORT_AGE_GROUPS_ACTIVITY))
            }
        }
    }
}

private fun Context.isInstalled(packageName: String): Boolean = try {
    packageManager.getApplicationInfo(packageName, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}
