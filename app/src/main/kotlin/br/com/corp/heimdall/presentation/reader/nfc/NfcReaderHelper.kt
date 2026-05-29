package br.com.corp.heimdall.presentation.reader.nfc

import android.nfc.tech.IsoDep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encapsula a transação ISO 7816-4 com o HCE do Huginn.
 *
 * Envia um SELECT com o AID proprietário `F05352425200` e decodifica a resposta
 * APDU removendo os 2 bytes de status `SW_OK (90 00)` do final.
 */
@Singleton
class NfcReaderHelper @Inject constructor() {

    /**
     * Envia SELECT APDU com o AID Huginn e retorna o payload do token como String.
     *
     * Resiliência:
     * - Aplica [ISO_DEP_TIMEOUT_MS] no driver NFC ([IsoDep.setTimeout]).
     * - Envolve a transação em [withTimeout] para cobrir OEMs que ignoram o timeout do driver.
     * - Garante [IsoDep.close] no bloco `finally`, mesmo em caso de exceção (evita resource leak).
     *
     * @param isoDep Tag NFC obtida via `IsoDep.get(tag)`.
     * @return String do token decodificado, ou `null` em caso de falha, timeout ou resposta inválida.
     */
    suspend fun sendSelectApdu(isoDep: IsoDep): String? = withContext(Dispatchers.IO) {
        try {
            isoDep.timeout = ISO_DEP_TIMEOUT_MS
            if (!isoDep.isConnected) {
                isoDep.connect()
            }
            val response = withTimeout(TRANSCEIVE_TIMEOUT_MS) {
                isoDep.transceive(SELECT_AID_APDU)
            }
            parseApduResponse(response)
        } catch (timeout: TimeoutCancellationException) {
            // Tag lenta / ataque de slow-read — aborta sem bloquear a thread.
            null
        } catch (cancel: CancellationException) {
            // Cancelamento estruturado real (ex: escopo cancelado) — propaga.
            throw cancel
        } catch (e: Exception) {
            // IOException, TagLostException, etc. — retorna null sem propagar.
            null
        } finally {
            runCatching { isoDep.close() }
        }
    }

    /**
     * Valida e extrai o payload da resposta APDU.
     *
     * A resposta deve terminar com `SW_OK (0x90, 0x00)`.
     * O payload é tudo que precede esses 2 bytes, decodificado como UTF-8.
     *
     * @return Payload como String, ou `null` se a resposta for inválida.
     */
    private fun parseApduResponse(response: ByteArray?): String? {
        if (response == null || response.size < 2) return null

        val sw1 = response[response.size - 2]
        val sw2 = response[response.size - 1]

        if (sw1 != SW_OK_1 || sw2 != SW_OK_2) return null

        val payloadBytes = response.copyOf(response.size - 2)
        if (payloadBytes.isEmpty()) return null

        return String(payloadBytes, Charsets.UTF_8)
    }

    companion object {
        /** Timeout do driver NFC para a transação APDU, em milissegundos. */
        private const val ISO_DEP_TIMEOUT_MS = 2_000

        /** Timeout da coroutine — margem extra sobre [ISO_DEP_TIMEOUT_MS] para OEMs que ignoram o driver. */
        private const val TRANSCEIVE_TIMEOUT_MS = 2_500L

        /** AID proprietário do HCE do Huginn: `F0 53 52 42 52 00`. */
        private val SELECT_AID_APDU = byteArrayOf(
            0x00,                           // CLA
            0xA4.toByte(),                  // INS: SELECT
            0x04,                           // P1: select by AID
            0x00,                           // P2
            0x06,                           // Lc: tamanho do AID (6 bytes)
            0xF0.toByte(), 0x53, 0x52, 0x42, 0x52, 0x00, // AID: F05352425200
            0x00,                           // Le: esperamos resposta de tamanho variável
        )

        private const val SW_OK_1: Byte = 0x90.toByte()
        private const val SW_OK_2: Byte = 0x00
    }
}
