package com.companyb.companyapp.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual fun createTokenStore(): TokenStore {
    val context = AndroidAppContext.context
    return AndroidTokenStore(context)
}

object AndroidAppContext {
    lateinit var context: Context
}

private const val PREFS_NAME = "companyapp_auth"
private const val KEY_TOKEN = "jwt_token"

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
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    override fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }
}

actual val defaultBaseUrl: String = "http://10.0.2.2:3023"
