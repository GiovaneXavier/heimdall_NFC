package br.com.corp.heimdall.core.util

import org.junit.Assert.*
import org.junit.Test

class TimeProviderTest {

    @Test
    fun `SystemTimeProvider returns value greater than reference epoch`() {
        val provider = SystemTimeProvider()
        // 1_700_000_000L = Nov 2023; qualquer execução após essa data deve ser maior
        assertTrue(provider.nowSeconds() > 1_700_000_000L)
    }

    @Test
    fun `SystemTimeProvider returns consistent value within same second`() {
        val provider = SystemTimeProvider()
        val t1 = provider.nowSeconds()
        val t2 = provider.nowSeconds()
        // Duas chamadas imediatas não devem diferir por mais de 1 segundo
        assertTrue(t2 - t1 <= 1L)
    }

    @Test
    fun `fake TimeProvider can be constructed for testing`() {
        val fixed = 1_710_000_000L
        val fake = object : TimeProvider {
            override fun nowSeconds() = fixed
        }
        assertEquals(fixed, fake.nowSeconds())
    }
}
