package com.companyb.companyapp.util

/**
 * #154 (D6) — platform file-save for export downloads. The VM fetches the export bytes and
 * surfaces them as a success payload; the screen calls this at the platform boundary
 * (save dialogs / Downloads are presentation, not VM logic).
 *
 * @return true when the file was written; false when the user cancelled or the write failed
 * (desktop save dialog cancel, Android write error — callers show an in-place error).
 */
expect fun saveDownload(
    fileName: String,
    bytes: ByteArray,
): Boolean
