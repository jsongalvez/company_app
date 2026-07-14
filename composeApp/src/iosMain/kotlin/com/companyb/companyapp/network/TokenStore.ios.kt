package com.companyb.companyapp.network

import platform.Foundation.NSUserDefaults

actual fun createTokenStore(): TokenStore = IosTokenStore()

private const val KEY_TOKEN = "companyapp_jwt_token"

class IosTokenStore : TokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun saveToken(token: String) {
        defaults.setObject(token, forKey = KEY_TOKEN)
        defaults.synchronize()
    }

    override fun getToken(): String? = defaults.stringForKey(KEY_TOKEN)

    override fun clearToken() {
        defaults.removeObjectForKey(KEY_TOKEN)
        defaults.synchronize()
    }
}

actual val defaultBaseUrl: String = "http://localhost:3023"
