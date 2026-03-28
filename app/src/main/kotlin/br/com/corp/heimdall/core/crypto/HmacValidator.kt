package br.com.corp.heimdall.core.crypto

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Valida assinaturas HMAC-SHA256 em tempo constante.
 *
 * Utiliza [MessageDigest.isEqual] para comparação, prevenindo timing attacks.
 * Nunca lança exceção para o chamador — retorna `false` em caso de erro interno.
 */
@Singleton
class HmacValidator @Inject constructor() {

    /**
     * Verifica se [expectedBase64Url] é a assinatura HMAC-SHA256 correta de [data] com [key].
     *
     * @param key              Chave secreta em texto claro.
     * @param data             Payload que foi assinado.
     * @param expectedBase64Url Assinatura esperada em Base64url (com ou sem padding `=`).
     * @return `true` se a assinatura for válida; `false` caso contrário ou em erro.
     */
    fun verify(key: String, data: String, expectedBase64Url: String): Boolean {
        if (key.isEmpty() || data.isEmpty() || expectedBase64Url.isEmpty()) return false
        return try {
            val computed = computeHmac(key, data)
            val expected = decodeBase64Url(expectedBase64Url) ?: return false
            MessageDigest.isEqual(computed, expected)
        } catch (_: Exception) {
            false
        }
    }

    private fun computeHmac(key: String, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun decodeBase64Url(input: String): ByteArray? {
        // Remove padding existente (pode ser inválido) e adiciona padding correto.
        // Usa getUrlDecoder() que rejeita caracteres Base64 padrão (+/) — apenas aceita Base64url (-_).
        val stripped = input.trimEnd('=')
        val padded = when (stripped.length % 4) {
            0 -> stripped
            2 -> "$stripped=="
            3 -> "$stripped="
            else -> return null  // comprimento % 4 == 1 é inválido
        }
        return try {
            Base64.getUrlDecoder().decode(padded)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
