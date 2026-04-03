package br.com.corp.heimdall.presentation.maintenance

import br.com.corp.heimdall.data.local.preferences.ConfigPreferences
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences.Channel
import br.com.corp.heimdall.domain.repository.AuditLogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MaintenanceViewModelTest {

    private val config: ConfigPreferences = mockk(relaxed = true)
    private val auditLog: AuditLogRepository = mockk(relaxed = true)
    private lateinit var viewModel: MaintenanceViewModel

    @Before
    fun setup() {
        viewModel = MaintenanceViewModel(config, auditLog)
    }

    @Test
    fun `PIN correto emite Unlocked`() {
        every { config.verifyPin("123456") } returns true

        viewModel.verifyPin("123456")

        assertEquals(MaintenanceUiState.Unlocked, viewModel.uiState.value)
    }

    @Test
    fun `PIN incorreto emite WrongPin com tentativas restantes`() {
        every { config.verifyPin(any()) } returns false

        viewModel.verifyPin("000000")

        val state = viewModel.uiState.value
        assertTrue(state is MaintenanceUiState.WrongPin)
        assertEquals(4, (state as MaintenanceUiState.WrongPin).attemptsRemaining)
    }

    @Test
    fun `PIN incorreto 5x emite LockedUntil`() {
        every { config.verifyPin(any()) } returns false

        repeat(5) { viewModel.verifyPin("000000") }

        val state = viewModel.uiState.value
        assertTrue("Esperado LockedUntil, obteve $state", state is MaintenanceUiState.LockedUntil)
        val locked = state as MaintenanceUiState.LockedUntil
        assertTrue(locked.untilMs > System.currentTimeMillis())
        assertTrue(locked.remainingSeconds in 59..60)
    }

    @Test
    fun `saveNewPin com PIN divergente retorna false e nao persiste`() {
        every { config.verifyPin("123456") } returns true
        viewModel.verifyPin("123456")

        val result = viewModel.saveNewPin("111111", "222222")

        assertEquals(false, result)
        verify(exactly = 0) { config.savePin(any()) }
    }

    @Test
    fun `saveNewPin com PIN valido retorna true e persiste`() {
        every { config.verifyPin("123456") } returns true
        viewModel.verifyPin("123456")

        val result = viewModel.saveNewPin("654321", "654321")

        assertEquals(true, result)
        verify { config.savePin("654321") }
    }

    @Test
    fun `dismiss retorna para Locked e limpa tentativas`() {
        every { config.verifyPin(any()) } returns false
        repeat(3) { viewModel.verifyPin("000000") }

        viewModel.dismiss()

        assertEquals(MaintenanceUiState.Locked, viewModel.uiState.value)
    }
}
