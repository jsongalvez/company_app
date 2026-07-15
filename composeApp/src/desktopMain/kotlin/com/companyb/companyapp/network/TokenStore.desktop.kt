package com.companyb.companyapp.network

import com.companyb.companyapp.config.TOKEN_STORE_KEY
import com.companyb.companyapp.util.logInfo
import java.io.File

actual fun createTokenStore(): TokenStore = DesktopTokenStore()

private const val TOKEN_DIR = ".companyapp"

class DesktopTokenStore : TokenStore {
    private val tokenFile: File
        get() = File(System.getProperty("user.home"), "$TOKEN_DIR/$TOKEN_STORE_KEY")

    override fun saveToken(token: String) {
        logInfo("TokenStore", "saveToken: length=${token.length}")
        tokenFile.parentFile.mkdirs()
        tokenFile.writeText(token)
    }

    override fun getToken(): String? {
        val file = tokenFile
        val found = file.exists() && file.readText().trim().isNotEmpty()
        logInfo("TokenStore", "getToken: found=$found")
        return if (file.exists()) file.readText().trim().ifEmpty { null } else null
    }

    override fun clearToken() {
        logInfo("TokenStore", "clearToken")
        tokenFile.delete()
    }
}
