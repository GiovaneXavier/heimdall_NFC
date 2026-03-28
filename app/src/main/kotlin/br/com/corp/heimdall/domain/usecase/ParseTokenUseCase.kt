package br.com.corp.heimdall.domain.usecase

import br.com.corp.heimdall.domain.model.Token
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Faz o parse de uma string bruta e retorna o [Token] correspondente.
 *
 * Ordem de tentativa:
 * 1. Formato novo Huginn: `deviceId|employeeId|systemId|timestamp|nonce.hmacBase64url`
 * 2. Formato legado: `CORP.XXXX` ou `PART.XXXX` (XXXX = dígitos)
 * 3. [Token.Invalid] se nenhum formato for reconhecido
 */
@Singleton
class ParseTokenUseCase @Inject constructor() {

    private val legacyRegex = Regex("^(CORP|PART)\\.(\\d+)$")

    /**
     * Tenta interpretar [raw] como um token válido.
     *
     * @param raw String lida pelo leitor NFC ou QR.
     * @return [Token.New], [Token.Legacy] ou [Token.Invalid].
     */
    operator fun invoke(raw: String): Token {
        if (raw.isBlank()) return Token.Invalid

        parseNew(raw)?.let { return it }
        parseLegacy(raw)?.let { return it }
        return Token.Invalid
    }

    // ── Formato novo ─────────────────────────────────────────────────────────

    private fun parseNew(raw: String): Token.New? {
        // Deve conter exatamente um '.'
        val dotIndex = raw.indexOf('.')
        if (dotIndex < 0 || raw.indexOf('.', dotIndex + 1) >= 0) return null

        val payload = raw.substring(0, dotIndex)
        val hmac    = raw.substring(dotIndex + 1)

        if (hmac.isBlank()) return null

        // O payload deve conter exatamente 4 pipes '|'
        val parts = payload.split("|")
        if (parts.size != 5) return null

        val (deviceId, employeeId, systemId, timestampStr, nonce) = parts
        if (deviceId.isBlank() || employeeId.isBlank() || systemId.isBlank()
            || timestampStr.isBlank() || nonce.isBlank()) return null

        val timestamp = timestampStr.toLongOrNull() ?: return null

        return Token.New(
            deviceId   = deviceId,
            employeeId = employeeId,
            systemId   = systemId,
            timestamp  = timestamp,
            nonce      = nonce,
            hmac       = hmac,
        )
    }

    // ── Formato legado ────────────────────────────────────────────────────────

    private fun parseLegacy(raw: String): Token.Legacy? {
        val match = legacyRegex.matchEntire(raw) ?: return null
        return Token.Legacy(
            prefix = match.groupValues[1],
            code   = match.groupValues[2],
        )
    }
}
