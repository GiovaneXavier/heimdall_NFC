package br.com.corp.heimdall.domain.model

import org.junit.Assert.*
import org.junit.Test

class TokenTest {

    private val sampleNew = Token.New(
        deviceId   = "DEVICE-001",
        employeeId = "EMP001",
        systemId   = "SRBR_EXIT",
        timestamp  = 1700000000L,
        nonce      = "abc123",
        hmac       = "validHmacBase64url",
    )

    @Test
    fun `Token_New equality holds for identical data`() {
        val copy = sampleNew.copy()
        assertEquals(sampleNew, copy)
    }

    @Test
    fun `Token_New copy creates independent instance with modified field`() {
        val modified = sampleNew.copy(employeeId = "EMP002")
        assertNotEquals(sampleNew, modified)
        assertEquals("EMP002", modified.employeeId)
        assertEquals(sampleNew.deviceId, modified.deviceId)
    }

    @Test
    fun `Token_Legacy equality holds`() {
        val a = Token.Legacy(prefix = "CORP", code = "1234")
        val b = Token.Legacy(prefix = "CORP", code = "1234")
        assertEquals(a, b)
    }

    @Test
    fun `Token_Legacy copy creates independent instance`() {
        val a = Token.Legacy(prefix = "CORP", code = "1234")
        val b = a.copy(code = "5678")
        assertEquals("5678", b.code)
        assertEquals("CORP", b.prefix)
        assertNotEquals(a, b)
    }

    @Test
    fun `Token_Invalid is singleton-like object`() {
        assertSame(Token.Invalid, Token.Invalid)
    }

    @Test
    fun `sealed class variants are mutually exclusive`() {
        val tokens: List<Token> = listOf(sampleNew, Token.Legacy("CORP", "1"), Token.Invalid)
        assertEquals(1, tokens.filterIsInstance<Token.New>().size)
        assertEquals(1, tokens.filterIsInstance<Token.Legacy>().size)
        assertEquals(1, tokens.filterIsInstance<Token.Invalid>().size)
    }
}
