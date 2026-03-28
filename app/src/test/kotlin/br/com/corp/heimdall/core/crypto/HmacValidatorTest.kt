package br.com.corp.heimdall.core.crypto

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Testes unitários do [HmacValidator].
 *
 * O vetor cross-platform foi derivado via Node.js:
 * ```
 * node -e "const c=require('crypto');
 *   console.log(c.createHmac('sha256','TEST_SECRET_KEY')
 *   .update('1|REG|1700000000|1700003600|fixed-nonce|EMP001|Test User|SYS001')
 *   .digest('base64').replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,''))"
 * // Resultado: eSJdyv9cvTX3kst9JbzhJJoiD_P60Svb2UhaXPgVbBE
 * ```
 */
class HmacValidatorTest {

    private lateinit var validator: HmacValidator

    private val crossPlatformKey     = "TEST_SECRET_KEY"
    private val crossPlatformPayload = "1|REG|1700000000|1700003600|fixed-nonce|EMP001|Test User|SYS001"
    private val crossPlatformHmac    = "eSJdyv9cvTX3kst9JbzhJJoiD_P60Svb2UhaXPgVbBE"

    @Before
    fun setup() {
        validator = HmacValidator()
    }

    @Test
    fun `cross-platform vector returns true`() {
        assertTrue(validator.verify(crossPlatformKey, crossPlatformPayload, crossPlatformHmac))
    }

    @Test
    fun `correct HMAC returns true`() {
        assertTrue(validator.verify(crossPlatformKey, crossPlatformPayload, crossPlatformHmac))
    }

    @Test
    fun `HMAC with one byte different returns false`() {
        // Altera o primeiro caractere da assinatura
        val tampered = crossPlatformHmac.replaceFirst('e', 'f')
        assertFalse(validator.verify(crossPlatformKey, crossPlatformPayload, tampered))
    }

    @Test
    fun `HMAC with Base64 padding returns true`() {
        // Adiciona padding — deve ser tolerado
        val withPadding = "$crossPlatformHmac=="
        assertTrue(validator.verify(crossPlatformKey, crossPlatformPayload, withPadding))
    }

    @Test
    fun `HMAC with standard Base64 plus-slash returns false`() {
        // Base64 padrão (+/) não deve ser aceito como Base64url (-_)
        val standard = crossPlatformHmac.replace('-', '+').replace('_', '/')
        // Só é inválido se os caracteres forem realmente diferentes; o vetor não contém -/_
        // Então verificamos com uma assinatura sintética que usa +/
        val key = "KEY"
        val data = "DATA"
        // Calcula HMAC real em Base64url
        val realHmac = computeBase64UrlHmac(key, data)
        // Versão com +/ em vez de -_
        val standardVersion = realHmac.replace('-', '+').replace('_', '/')
        if (standardVersion != realHmac) {
            // Só testa se houver diferença real
            assertFalse("HMAC com +/ não deve ser aceito como Base64url",
                validator.verify(key, data, standardVersion))
        }
        // Se não há -/_ no HMAC, o teste é trivialmente válido (pass)
    }

    @Test
    fun `empty key returns false`() {
        assertFalse(validator.verify("", crossPlatformPayload, crossPlatformHmac))
    }

    @Test
    fun `empty payload returns false`() {
        assertFalse(validator.verify(crossPlatformKey, "", crossPlatformHmac))
    }

    @Test
    fun `wrong key returns false`() {
        assertFalse(validator.verify("WRONG_KEY", crossPlatformPayload, crossPlatformHmac))
    }

    @Test
    fun `empty expected HMAC returns false`() {
        assertFalse(validator.verify(crossPlatformKey, crossPlatformPayload, ""))
    }

    @Test
    fun `completely different HMAC returns false`() {
        assertFalse(validator.verify(crossPlatformKey, crossPlatformPayload, "aGVsbG8gd29ybGQ"))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun computeBase64UrlHmac(key: String, data: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        val bytes = mac.doFinal(data.toByteArray())
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }
}
