package com.companyb.companyapp.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene

/** Presents iOS share sheet with export file. The user chooses Files or another installed app. */
@OptIn(ExperimentalForeignApi::class)
actual fun saveDownload(
    fileName: String,
    bytes: ByteArray,
): Boolean {
    if (bytes.isEmpty()) {
        logError("SaveDownload", "iOS export payload is empty")
        return false
    }
    val safeFileName = fileName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "export" }
    val temporaryUrl =
        NSURL.fileURLWithPath(
            "${NSFileManager.defaultManager.temporaryDirectory.path}/$safeFileName",
        )
    val data = bytes.toNSData()
    if (!data.writeToURL(temporaryUrl, atomically = true)) {
        logError("SaveDownload", "iOS export write failed")
        return false
    }

    val presenter = activeWindow()?.rootViewController?.topViewController()
    if (presenter == null) {
        logError("SaveDownload", "iOS export has no root view controller")
        return false
    }
    val activityController =
        UIActivityViewController(
            activityItems = listOf(temporaryUrl),
            applicationActivities = null,
        )
    activityController.popoverPresentationController?.apply {
        sourceView = presenter.view
        sourceRect = presenter.view.bounds
    }
    presenter.presentViewController(
        activityController,
        animated = true,
        completion = null,
    )
    return true
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

private fun activeWindow() =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { it.windows }
        .firstOrNull { it.isKeyWindow }

private fun UIViewController.topViewController(): UIViewController {
    val presented = presentedViewController
    return when {
        presented != null -> presented.topViewController()
        else -> this
    }
}
