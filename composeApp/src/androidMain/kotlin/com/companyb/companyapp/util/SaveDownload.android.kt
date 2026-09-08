package com.companyb.companyapp.util

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import com.companyb.companyapp.network.AndroidAppContext

/**
 * #154 (D6) — Android export save: write to the public Downloads collection via MediaStore
 * (API 29+; no storage permission needed for MediaStore.Downloads on modern Android). API 24-28
 * would need the legacy external-storage path + WRITE_EXTERNAL_STORAGE permission — the app
 * declares none, so those devices fail closed with a logged error (alpha device assumption:
 * internal ops app runs on API 29+ hardware; documented SOFT).
 */
actual fun saveDownload(
    fileName: String,
    bytes: ByteArray,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        logError("SaveDownload", "export save unsupported below API 29 (device API ${Build.VERSION.SDK_INT})")
        return false
    }
    // #651 max-2: resolver-null and MediaStore-write legs share one exit (inner
    // zero-row flip is an expression value, not an early return).
    val resolver =
        runCatching { AndroidAppContext.context.contentResolver }.getOrElse {
            logError("SaveDownload", "no application context for MediaStore insert", it)
            null
        }
    return if (resolver == null) {
        false
    } else {
        runCatching {
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeFor(fileName))
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert returned null")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("openOutputStream returned null")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            val updated = resolver.update(uri, values, null, null)
            if (updated == 0) {
                // Pass-9 HARD (count-0 family) — a 0-row flip means the file stays hidden in
                // Downloads: success must not be derived from a 0-row write.
                logError("SaveDownload", "MediaStore IS_PENDING flip updated 0 rows — the file stays hidden")
                false
            } else {
                true
            }
        }.onFailure { e ->
            logError("SaveDownload", "android save failed: ${e.message ?: "unknown"}", e)
        }.getOrDefault(false)
    }
}

private fun mimeFor(fileName: String): String =
    when {
        fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
        else -> "application/octet-stream"
    }
