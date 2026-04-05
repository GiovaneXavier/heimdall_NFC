package br.com.corp.heimdall.presentation.reader.nfc

import android.nfc.tech.IsoDep
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
     * Informações de debug da última leitura APDU.
     * Atualizado a cada chamada de [sendSelectApdu], inclusive em caso de erro.
     */
    @Volatile
    var lastDebugInfo: String? = null
        private set

    /**
     * Envia SELECT APDU com o AID Huginn e retorna o payload do token como String.
     *
     * @param isoDep Tag NFC obtida via `IsoDep.get(tag)`.
     * @return String do token decodificado, ou `null` em caso de falha ou resposta inválida.
     */
    fun sendSelectApdu(isoDep: IsoDep): String? {
        var response: ByteArray? = null
        return try {
            if (!isoDep.isConnected) {
                isoDep.connect()
            }
            response = isoDep.transceive(SELECT_AID_APDU)
            lastDebugInfo = buildDebugInfo(response, exception = null)
            parseApduResponse(response)
        } catch (e: Exception) {
            // IOException, TagLostException, etc. — retorna null sem propagar
            lastDebugInfo = buildDebugInfo(response, exception = e)
            null
        }
    }

    private fun buildDebugInfo(response: ByteArray?, exception: Exception?): String = buildString {
        if (exception != null) {
            appendLine("ERRO: ${exception.javaClass.simpleName}")
            appendLine("Msg:  ${exception.message}")
            if (response != null) appendLine("Hex:  ${response.toHexString()}")
            return@buildString
        }
        if (response == null) {
            appendLine("Resposta: null")
            return@buildString
        }
        appendLine("Tamanho: ${response.size} bytes")
        appendLine("Hex:     ${response.toHexString()}")
        if (response.size >= 2) {
            val sw1 = response[response.size - 2]
            val sw2 = response[response.size - 1]
            val swOk = sw1 == SW_OK_1 && sw2 == SW_OK_2
            appendLine("SW:      %02X %02X  (%s)".format(sw1.toInt() and 0xFF, sw2.toInt() and 0xFF, if (swOk) "OK" else "ERRO"))
            if (swOk && response.size > 2) {
                val decoded = String(response.copyOf(response.size - 2), Charsets.UTF_8)
                appendLine("Payload: $decoded")
            }
        } else {
            appendLine("Resposta muito curta (< 2 bytes)")
        }
    }.trimEnd()

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

private fun ByteArray.toHexString(): String =
    joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
