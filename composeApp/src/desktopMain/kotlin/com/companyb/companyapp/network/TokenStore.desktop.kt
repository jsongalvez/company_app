package com.companyb.companyapp.network

import com.companyb.companyapp.config.TOKEN_STORE_KEY
import java.io.File

actual fun createTokenStore(): TokenStore = DesktopTokenStore()

private const val TOKEN_DIR = ".companyapp"

class DesktopTokenStore : TokenStore {
    private val tokenFile: File
        get() = File(System.getProperty("user.home"), "$TOKEN_DIR/$TOKEN_STORE_KEY")

    override fun saveToken(token: String) {
        tokenFile.parentFile.mkdirs()
        tokenFile.writeText(token)
    }

    override fun getToken(): String? {
        val file = tokenFile
        return if (file.exists()) file.readText().trim().ifEmpty { null } else null
    }

    override fun clearToken() {
        tokenFile.delete()
    }
}
