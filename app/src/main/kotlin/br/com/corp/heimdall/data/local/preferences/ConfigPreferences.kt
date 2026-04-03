package br.com.corp.heimdall.data.local.preferences

import br.com.corp.heimdall.core.security.SecurePreferences
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste a configuração operacional do ponto de acesso: canal, systemId e hash do PIN.
 *
 * Usa [SecurePreferences] (EncryptedSharedPreferences) para armazenar os dados.
 */
@Singleton
class ConfigPreferences @Inject constructor(
    private val prefs: SecurePreferences,
) {

    /** Canal de leitura selecionado na configuração inicial. */
    var channel: Channel?
        get() = prefs.getString(KEY_CHANNEL)?.let { Channel.valueOf(it) }
        set(value) = if (value != null) prefs.putString(KEY_CHANNEL, value.name)
                     else prefs.remove(KEY_CHANNEL)

    /** Identificador do sistema configurado neste ponto de acesso (ex: `ACCESS_HQ`). */
    var systemId: String
        get() = prefs.getString(KEY_SYSTEM_ID) ?: ""
        set(value) = prefs.putString(KEY_SYSTEM_ID, value)

    /** Timestamp da última sincronização de audit log bem-sucedida (milissegundos Unix). */
    var lastAuditSyncMs: Long
        get() = prefs.getString(KEY_LAST_AUDIT_SYNC_MS)?.toLongOrNull() ?: 0L
        set(value) = prefs.putString(KEY_LAST_AUDIT_SYNC_MS, value.toString())

    /** Retorna `true` se o setup inicial já foi concluído. */
    val isConfigured: Boolean
        get() = prefs.contains(KEY_CHANNEL) && systemId.isNotBlank() && prefs.contains(KEY_PIN_HASH)

    /**
     * Salva o PIN de manutenção como SHA-256(salt + PIN).
     * O salt é gerado aleatoriamente e armazenado junto ao hash.
     */
    fun savePin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.toHex()
        val hash = sha256(saltHex + pin)
        prefs.putString(KEY_PIN_SALT, saltHex)
        prefs.putString(KEY_PIN_HASH, hash)
    }

    /**
     * Verifica se o PIN fornecido corresponde ao hash armazenado.
     * Retorna `false` se nenhum PIN estiver configurado.
     */
    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT) ?: return false
        val storedHash = prefs.getString(KEY_PIN_HASH) ?: return false
        val computedHash = sha256(salt + pin)
        return MessageDigest.isEqual(computedHash.toByteArray(), storedHash.toByteArray())
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Canal de leitura suportado pelo dispositivo Heimdall. */
    enum class Channel { NFC, QR }

    companion object {
        private const val KEY_CHANNEL = "config_channel"
        private const val KEY_SYSTEM_ID = "configured_system_id"
        private const val KEY_PIN_HASH = "maintenance_pin_hash"
        private const val KEY_PIN_SALT = "maintenance_pin_salt"
        private const val KEY_LAST_AUDIT_SYNC_MS = "last_audit_sync_ms"
    }
}
