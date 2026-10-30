package com.ricardofrango.squadify.coach.presentation.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberTextSharing(): TextSharing = remember {
    object : TextSharing {
        override fun share(text: String) {
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
            // The top-most screen presents it (the Compose screen may already present something).
            var top = root
            while (top.presentedViewController != null) top = top.presentedViewController!!
            val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            // On an iPad the sheet is a popover, which needs somewhere to point from.
            sheet.popoverPresentationController?.sourceView = top.view
            top.presentViewController(sheet, animated = true, completion = null)
        }

        override fun copy(text: String) {
            UIPasteboard.generalPasteboard.string = text
        }
    }
}
