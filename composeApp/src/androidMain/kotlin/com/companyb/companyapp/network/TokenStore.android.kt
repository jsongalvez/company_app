package com.companyb.companyapp.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.companyb.companyapp.config.TOKEN_STORE_KEY
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn

actual fun createTokenStore(): TokenStore {
    val context = AndroidAppContext.context
    return AndroidTokenStore(context)
}

object AndroidAppContext {
    lateinit var context: Context
}

private const val PREFS_NAME = "companyapp_auth"

class AndroidTokenStore(
    context: Context,
) : TokenStore {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    @SuppressLint("GetInstance")
    @Suppress("TooGenericExceptionCaught")
    private fun createEncryptedPrefs(context: Context): SharedPreferences =
        try {
            val masterKey =
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            logWarn(
                "TokenStore",
                "Encrypted preferences unavailable; using regular preferences: ${e.message.orEmpty()}",
            )
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

    override fun saveToken(token: String) {
        logInfo("TokenStore", "saveToken: length=${token.length}")
        prefs.edit().putString(TOKEN_STORE_KEY, token).apply()
    }

    override fun getToken(): String? {
        val token = prefs.getString(TOKEN_STORE_KEY, null)
        logInfo("TokenStore", "getToken: found=${token != null}")
        return token
    }

    override fun clearToken() {
        logInfo("TokenStore", "clearToken")
        prefs.edit().remove(TOKEN_STORE_KEY).apply()
    }
}
