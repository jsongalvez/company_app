package com.companyb.companyapp.network

import com.companyb.companyapp.config.TOKEN_STORE_KEY
import platform.Foundation.NSUserDefaults

actual fun createTokenStore(): TokenStore = IosTokenStore()

class IosTokenStore : TokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun saveToken(token: String) {
        defaults.setObject(token, forKey = TOKEN_STORE_KEY)
        defaults.synchronize()
    }

    override fun getToken(): String? = defaults.stringForKey(TOKEN_STORE_KEY)

    override fun clearToken() {
        defaults.removeObjectForKey(TOKEN_STORE_KEY)
        defaults.synchronize()
    }
}
