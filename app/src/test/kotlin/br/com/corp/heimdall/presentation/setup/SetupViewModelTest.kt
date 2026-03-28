package br.com.corp.heimdall.presentation.setup

import br.com.corp.heimdall.data.local.preferences.ConfigPreferences
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences.Channel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val config: ConfigPreferences = mockk(relaxed = true)
    private lateinit var viewModel: SetupViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SetupViewModel(config)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `campo systemId vazio nao salva e emite Error`() = runTest {
        viewModel.saveConfig(Channel.NFC, "", "123456", "123456")

        val state = viewModel.uiState.value
        assertTrue("Esperado Error, obteve $state", state is SetupUiState.Error)
        verify(exactly = 0) { config.channel = any() }
    }

    @Test
    fun `PIN divergente nao salva e emite Error`() = runTest {
        viewModel.saveConfig(Channel.NFC, "ACCESS_HQ", "123456", "654321")

        val state = viewModel.uiState.value
        assertTrue("Esperado Error, obteve $state", state is SetupUiState.Error)
        verify(exactly = 0) { config.channel = any() }
    }

    @Test
    fun `PIN com menos de 6 digitos nao salva e emite Error`() = runTest {
        viewModel.saveConfig(Channel.NFC, "ACCESS_HQ", "12345", "12345")

        val state = viewModel.uiState.value
        assertTrue("Esperado Error, obteve $state", state is SetupUiState.Error)
        verify(exactly = 0) { config.channel = any() }
    }

    @Test
    fun `PIN nao numerico nao salva e emite Error`() = runTest {
        viewModel.saveConfig(Channel.NFC, "ACCESS_HQ", "12345a", "12345a")

        val state = viewModel.uiState.value
        assertTrue("Esperado Error, obteve $state", state is SetupUiState.Error)
    }

    @Test
    fun `dados validos salva e emite NavigateToReader`() = runTest {
        viewModel.saveConfig(Channel.QR, "ACCESS_HQ", "123456", "123456")

        val state = viewModel.uiState.value
        assertEquals(SetupUiState.NavigateToReader, state)
        verify { config.channel = Channel.QR }
        verify { config.systemId = "ACCESS_HQ" }
        verify { config.savePin("123456") }
    }

    @Test
    fun `clearError retorna para Idle`() = runTest {
        viewModel.saveConfig(Channel.NFC, "", "123456", "123456")
        assertTrue(viewModel.uiState.value is SetupUiState.Error)

        viewModel.clearError()
        assertEquals(SetupUiState.Idle, viewModel.uiState.value)
    }
}
