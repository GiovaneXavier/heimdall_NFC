package br.com.corp.heimdall.domain.usecase

import br.com.corp.heimdall.domain.model.Token
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ParseTokenUseCaseTest {

    private lateinit var useCase: ParseTokenUseCase

    private val validNewToken =
        "DEVICE001|EMP001|SRBR_EXIT|1700000000|abc123.eSJdyv9cvTX3kst9JbzhJJoiD_P60Svb2UhaXPgVbBE"

    @Before
    fun setup() {
        useCase = ParseTokenUseCase()
    }

    // ── Token novo ────────────────────────────────────────────────────────────

    @Test
    fun `valid new token returns Token_New with correct fields`() {
        val result = useCase(validNewToken) as Token.New
        assertEquals("DEVICE001", result.deviceId)
        assertEquals("EMP001", result.employeeId)
        assertEquals("SRBR_EXIT", result.systemId)
        assertEquals(1700000000L, result.timestamp)
        assertEquals("abc123", result.nonce)
        assertEquals("eSJdyv9cvTX3kst9JbzhJJoiD_P60Svb2UhaXPgVbBE", result.hmac)
    }

    @Test
    fun `new token without dot returns Token_Invalid`() {
        val result = useCase("DEVICE001|EMP001|SRBR_EXIT|1700000000|abc123 validHmac")
        assertEquals(Token.Invalid, result)
    }

    @Test
    fun `new token with 3 pipes returns Token_Invalid`() {
        val result = useCase("DEVICE001|EMP001|SRBR_EXIT|1700000000.hmac")
        assertEquals(Token.Invalid, result)
    }

    @Test
    fun `new token with 5 pipes returns Token_Invalid`() {
        val result = useCase("A|B|C|D|E|F.hmac")
        assertEquals(Token.Invalid, result)
    }

    @Test
    fun `new token with non-numeric timestamp returns Token_Invalid`() {
        val result = useCase("DEVICE001|EMP001|SRBR_EXIT|NOTANUMBER|nonce.hmac")
        assertEquals(Token.Invalid, result)
    }

    @Test
    fun `new token with all blank fields returns Token_Invalid`() {
        val result = useCase("|||  |  .hmac")
        assertEquals(Token.Invalid, result)
    }

    @Test
    fun `new token with multiple dots returns Token_Invalid`() {
        val result = useCase("DEVICE001|EMP001|SRBR_EXIT|1700000000|abc123.hmac.extra")
        assertEquals(Token.Invalid, result)
    }

    // ── Token legado ──────────────────────────────────────────────────────────

    @Test
    fun `CORP dot numeric returns Token_Legacy with correct fields`() {
        val result = useCase("CORP.1234") as Token.Legacy
        assertEquals("CORP", result.prefix)
        assertEquals("1234", result.code)
    }

    @Test
    fun `PART dot numeric returns Token_Legacy`() {
        val result = useCase("PART.5678") as Token.Legacy
        assertEquals("PART", result.prefix)
        assertEquals("5678", result.code)
    }

    @Test
    fun `CORP dot non-numeric returns Token_Invalid`() {
        assertEquals(Token.Invalid, useCase("CORP.abc"))
    }

    @Test
    fun `unknown prefix returns Token_Invalid`() {
        assertEquals(Token.Invalid, useCase("ACME.1234"))
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `empty string returns Token_Invalid`() {
        assertEquals(Token.Invalid, useCase(""))
    }

    @Test
    fun `blank string returns Token_Invalid`() {
        assertEquals(Token.Invalid, useCase("   "))
    }

    @Test
    fun `random string returns Token_Invalid`() {
        assertEquals(Token.Invalid, useCase("xPt9#!@random"))
    }
}
