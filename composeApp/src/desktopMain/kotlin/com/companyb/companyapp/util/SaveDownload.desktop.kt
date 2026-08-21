package com.companyb.companyapp.util

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * #154 (D6) — desktop save dialog (native FileDialog; the app runs on the AWT EDT, so a direct
 * blocking call is safe). Cancel → false, never an exception.
 */
actual fun saveDownload(
    fileName: String,
    bytes: ByteArray,
): Boolean {
    val dialog = FileDialog(null as Frame?, "Save export", FileDialog.SAVE)
    dialog.file = fileName
    dialog.isVisible = true
    val chosen = dialog.file ?: return false
    return runCatching {
        val target = File(dialog.directory.orEmpty(), chosen)
        target.writeBytes(bytes)
    }.onFailure { e ->
        logError("SaveDownload", "desktop save failed: ${e.message ?: "unknown"}", e)
    }.isSuccess
}
