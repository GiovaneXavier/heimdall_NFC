package br.com.corp.heimdall.presentation.reader.nfc

import android.nfc.tech.IsoDep
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NfcReaderHelperTest {

    private lateinit var helper: NfcReaderHelper
    private val isoDep: IsoDep = mockk(relaxed = true)

    @Before
    fun setup() {
        helper = NfcReaderHelper()
        every { isoDep.isConnected } returns true
    }

    @Test
    fun `resposta com SW_OK retorna payload correto`() {
        val payload = "deviceId|empId|sys|1234567890|nonce"
        val response = payload.toByteArray(Charsets.UTF_8) + byteArrayOf(0x90.toByte(), 0x00)
        every { isoDep.transceive(any()) } returns response

        val result = helper.sendSelectApdu(isoDep)
        assertEquals(payload, result)
    }

    @Test
    fun `resposta sem SW_OK retorna null`() {
        val response = "somedata".toByteArray() + byteArrayOf(0x6A, 0x82.toByte())
        every { isoDep.transceive(any()) } returns response

        val result = helper.sendSelectApdu(isoDep)
        assertNull(result)
    }

    @Test
    fun `resposta null retorna null`() {
        every { isoDep.transceive(any()) } returns null

        val result = helper.sendSelectApdu(isoDep)
        assertNull(result)
    }

    @Test
    fun `resposta com apenas SW_OK e payload vazio retorna null`() {
        val response = byteArrayOf(0x90.toByte(), 0x00)
        every { isoDep.transceive(any()) } returns response

        val result = helper.sendSelectApdu(isoDep)
        assertNull(result)
    }

    @Test
    fun `TagLostException retorna null`() {
        every { isoDep.transceive(any()) } throws android.nfc.TagLostException("tag lost")

        val result = helper.sendSelectApdu(isoDep)
        assertNull(result)
    }

    @Test
    fun `IOException retorna null`() {
        every { isoDep.transceive(any()) } throws java.io.IOException("io error")

        val result = helper.sendSelectApdu(isoDep)
        assertNull(result)
    }

    @Test
    fun `IsoDep nao conectado chama connect antes de transceive`() {
        every { isoDep.isConnected } returns false
        val payload = "token"
        val response = payload.toByteArray() + byteArrayOf(0x90.toByte(), 0x00)
        every { isoDep.transceive(any()) } returns response

        helper.sendSelectApdu(isoDep)
        verify { isoDep.connect() }
    }
}
