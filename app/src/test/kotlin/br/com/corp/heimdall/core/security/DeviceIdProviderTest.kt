package br.com.corp.heimdall.core.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class DeviceIdProviderTest {

    private val prefs: SecurePreferences = mockk(relaxed = true)

    @Test
    fun `returns existing UUID when already stored`() {
        val storedId = "550e8400-e29b-41d4-a716-446655440000"
        every { prefs.getString(DeviceIdProvider.KEY_DEVICE_ID) } returns storedId

        val provider = DeviceIdProvider(prefs)
        assertEquals(storedId, provider.getDeviceId())
    }

    @Test
    fun `generates and persists UUID when not stored`() {
        every { prefs.getString(DeviceIdProvider.KEY_DEVICE_ID) } returns null

        val provider = DeviceIdProvider(prefs)
        val id = provider.getDeviceId()

        assertNotNull(id)
        assertTrue("UUID deve ter 36 caracteres", id.length == 36)
        assertTrue("UUID deve ter formato correto", id.matches(UUID_REGEX))
        verify(exactly = 1) { prefs.putString(DeviceIdProvider.KEY_DEVICE_ID, id) }
    }

    @Test
    fun `consecutive calls return same UUID`() {
        every { prefs.getString(DeviceIdProvider.KEY_DEVICE_ID) } returns null

        val provider = DeviceIdProvider(prefs)
        val id1 = provider.getDeviceId()
        val id2 = provider.getDeviceId()

        assertEquals(id1, id2)
        // putString deve ter sido chamado apenas 1x (na criação), não a cada chamada
        verify(exactly = 1) { prefs.putString(any(), any()) }
    }

    @Test
    fun `generated UUID is valid v4 format`() {
        every { prefs.getString(DeviceIdProvider.KEY_DEVICE_ID) } returns null

        val id = DeviceIdProvider(prefs).getDeviceId()

        assertTrue(id.matches(UUID_REGEX))
    }

    companion object {
        private val UUID_REGEX = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        )
    }
}
