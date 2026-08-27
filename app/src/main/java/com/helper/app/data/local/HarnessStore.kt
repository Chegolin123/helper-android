package com.helper.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Шифрованное хранение настроек харнесса: токен моста ZCode.
 * Токен вводится вручную в настройках приложения и НЕ зашит в APK.
 */
class HarnessStore private constructor(private val prefs: SharedPreferences) {

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: com.helper.app.BuildConfig.ZCODE_TOKEN.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    companion object {
        private const val KEY_TOKEN = "zcode_token"

        @Volatile
        private var instance: HarnessStore? = null

        fun get(context: Context): HarnessStore =
            instance ?: synchronized(this) {
                instance ?: create(context).also { instance = it }
            }

        private fun create(context: Context): HarnessStore {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val prefs = EncryptedSharedPreferences.create(
                "harness_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return HarnessStore(prefs)
        }
    }
}