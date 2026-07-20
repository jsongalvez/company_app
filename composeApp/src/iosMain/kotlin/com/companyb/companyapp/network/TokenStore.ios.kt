package com.companyb.companyapp.network

import com.companyb.companyapp.config.TOKEN_STORE_KEY
import com.companyb.companyapp.util.logInfo
import platform.Foundation.NSUserDefaults

actual fun createTokenStore(): TokenStore = IosTokenStore()

class IosTokenStore : TokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun saveToken(token: String) {
        logInfo("TokenStore", "saveToken: length=${token.length}")
        defaults.setObject(token, forKey = TOKEN_STORE_KEY)
        defaults.synchronize()
    }

    override fun getToken(): String? {
        val token = defaults.stringForKey(TOKEN_STORE_KEY)
        logInfo("TokenStore", "getToken: found=${token != null}")
        return token
    }

    override fun clearToken() {
        logInfo("TokenStore", "clearToken")
        defaults.removeObjectForKey(TOKEN_STORE_KEY)
        defaults.synchronize()
    }
}
