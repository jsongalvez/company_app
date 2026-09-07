package com.companyb.companyapp.network

import com.companyb.companyapp.config.TOKEN_STORE_KEY
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

actual fun createTokenStore(): TokenStore = DesktopTokenStore()

private const val TOKEN_DIR = ".companyapp"
private val OWNER_ONLY = PosixFilePermissions.fromString("rw-------")

class DesktopTokenStore(
    baseDir: File = File(System.getProperty("user.home"), TOKEN_DIR),
) : TokenStore {
    private val tokenFile = File(baseDir, TOKEN_STORE_KEY)

    override fun saveToken(token: String) {
        logInfo("TokenStore", "saveToken: length=${token.length}")
        tokenFile.parentFile.mkdirs()
        tokenFile.writeText(token)
        try {
            Files.setPosixFilePermissions(tokenFile.toPath(), OWNER_ONLY)
        } catch (e: UnsupportedOperationException) {
            // Non-POSIX filesystem (Windows): the file inherits the per-account ACL of the user profile.
            logError("TokenStore", "POSIX permissions unsupported; token relies on profile ACLs", e)
        } catch (e: IOException) {
            // Tightening failed after the write; never fail login over storage hardening.
            logError("TokenStore", "Could not restrict token file to owner-only access", e)
        }
    }

    override fun getToken(): String? {
        // #581 — an unreadable token file means unsigned; never fail a request over token storage.
        val token =
            try {
                if (tokenFile.exists()) tokenFile.readText().trim().ifEmpty { null } else null
            } catch (e: IOException) {
                logError("TokenStore", "Could not read token file; treating as signed-out", e)
                null
            }
        logInfo("TokenStore", "getToken: found=${token != null}")
        return token
    }

    override fun clearToken() {
        logInfo("TokenStore", "clearToken")
        tokenFile.delete()
    }
}
