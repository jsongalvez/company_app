package com.companyb.companyapp.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.companyb.companyapp.config.TOKEN_STORE_KEY

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
        } catch (_: Exception) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

    override fun saveToken(token: String) {
        prefs.edit().putString(TOKEN_STORE_KEY, token).apply()
    }

    override fun getToken(): String? = prefs.getString(TOKEN_STORE_KEY, null)

    override fun clearToken() {
        prefs.edit().remove(TOKEN_STORE_KEY).apply()
    }
}
