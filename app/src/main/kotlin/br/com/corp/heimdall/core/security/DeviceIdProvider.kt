package br.com.corp.heimdall.core.security

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gera e persiste um UUID v4 como identificador único do dispositivo Heimdall.
 *
 * Na primeira chamada, gera um UUID aleatório, salva em [SecurePreferences] e o retorna.
 * Em chamadas subsequentes, retorna o UUID armazenado. O ID nunca muda após a geração
 * inicial, garantindo que o admin de TI possa registrar o dispositivo na whitelist
 * do backend com segurança.
 *
 * @property prefs Armazenamento seguro onde o UUID é persistido.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    private val prefs: SecurePreferences,
) {
    private val cachedId: String by lazy { loadOrCreate() }

    /**
     * Retorna o UUID do dispositivo. Idempotente após a primeira chamada.
     *
     * @return UUID v4 formatado como string (ex: `"550e8400-e29b-41d4-a716-446655440000"`).
     */
    fun getDeviceId(): String = cachedId

    private fun loadOrCreate(): String {
        val stored = prefs.getString(KEY_DEVICE_ID)
        if (!stored.isNullOrBlank()) return stored

        val newId = UUID.randomUUID().toString()
        prefs.putString(KEY_DEVICE_ID, newId)
        return newId
    }

    companion object {
        internal const val KEY_DEVICE_ID = "heimdall_device_id"
    }
}
