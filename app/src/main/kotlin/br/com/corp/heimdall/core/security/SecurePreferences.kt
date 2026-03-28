package br.com.corp.heimdall.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper sobre [EncryptedSharedPreferences] com AES-256-GCM.
 *
 * Todos os dados persistidos por este wrapper são cifrados em repouso.
 * Deve ser a única forma de persistência de dados sensíveis no Heimdall.
 *
 * @param context Contexto da aplicação, injetado via Hilt.
 */
@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Lê um valor String, retornando [default] se a chave não existir. */
    fun getString(key: String, default: String? = null): String? =
        prefs.getString(key, default)

    /** Persiste um valor String. */
    fun putString(key: String, value: String) =
        prefs.edit().putString(key, value).apply()

    /** Remove uma chave. */
    fun remove(key: String) =
        prefs.edit().remove(key).apply()

    /** Verifica se a chave existe. */
    fun contains(key: String): Boolean = prefs.contains(key)

    companion object {
        private const val FILE_NAME = "heimdall_secure_prefs"
    }
}
